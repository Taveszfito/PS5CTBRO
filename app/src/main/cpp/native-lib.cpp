#include <jni.h>
#include <vector>
#include <deque>
#include <cstring>
#include <cstdlib>
#include <cerrno>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <pthread.h>
#include <mutex>

static int g_streamFd = -1;
static int g_streamInterfaceNumber = -1;
static int g_streamAltSetting = -1;
static int g_streamEndpointAddress = -1;
static bool g_streamRunning = false;
static pthread_t g_streamThread = 0;

static constexpr int PAYLOAD_SIZE = 384;
static constexpr int PACKETS_PER_URB = 32; // A stabilitás és az alacsony késleltetés egyensúlya
static constexpr int NUM_URBS = 8;        // Megemeltük a puffert, hogy ne szakadjon meg a folyamat

struct PendingUrbContext {
    usbdevfs_urb* urb = nullptr;
    unsigned char* buffer = nullptr;
    bool inUse = false;
};

static std::deque<std::vector<unsigned char>> g_pcmQueue;
static std::mutex g_pcmMutex;
static std::mutex g_urbMutex;

static void freeUrbContext(PendingUrbContext* ctx) {
    if (!ctx) return;
    if (ctx->buffer) {
        std::free(ctx->buffer);
        ctx->buffer = nullptr;
    }
    if (ctx->urb) {
        std::free(ctx->urb);
        ctx->urb = nullptr;
    }
    delete ctx;
}

static void* streamThreadFunction(void* /*arg*/) {
    int fd = g_streamFd;
    std::vector<PendingUrbContext*> urbPool;

    for (int i = 0; i < NUM_URBS; ++i) {
        size_t urbSize = sizeof(usbdevfs_urb) + PACKETS_PER_URB * sizeof(usbdevfs_iso_packet_desc);
        auto* urb = reinterpret_cast<usbdevfs_urb*>(std::calloc(1, urbSize));
        auto* buf = static_cast<unsigned char*>(std::malloc(PAYLOAD_SIZE * PACKETS_PER_URB));

        if (!urb || !buf) {
            if (urb) std::free(urb);
            if (buf) std::free(buf);
            continue;
        }

        auto* ctx = new PendingUrbContext{urb, buf, false};

        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(g_streamEndpointAddress);
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = buf;
        urb->buffer_length = PAYLOAD_SIZE * PACKETS_PER_URB;
        urb->number_of_packets = PACKETS_PER_URB;
        urb->usercontext = ctx;

        for (int p = 0; p < PACKETS_PER_URB; ++p) {
            urb->iso_frame_desc[p].length = PAYLOAD_SIZE;
        }

        urbPool.push_back(ctx);
    }

    while (g_streamRunning) {
        while (true) {
            void* reaped = nullptr;
            if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &reaped) < 0) break;
            if (reaped) {
                auto* done = reinterpret_cast<usbdevfs_urb*>(reaped);
                auto* ctx = reinterpret_cast<PendingUrbContext*>(done->usercontext);
                if (ctx) {
                    ctx->inUse = false;
                }
            }
        }

        PendingUrbContext* ctx = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_urbMutex);
            for (auto* u : urbPool) {
                if (!u->inUse) {
                    ctx = u;
                    ctx->inUse = true;
                    break;
                }
            }
        }

        if (!ctx) {
            usleep(100); // Gyorsabb ciklus
            continue;
        }

        std::vector<unsigned char> chunk;
        {
            std::lock_guard<std::mutex> lock(g_pcmMutex);
            if (!g_pcmQueue.empty()) {
                chunk = std::move(g_pcmQueue.front());
                g_pcmQueue.pop_front();
            }
        }

        if (chunk.empty()) {
            std::memset(ctx->buffer, 0, PAYLOAD_SIZE * PACKETS_PER_URB);
        } else {
            size_t copyLen = std::min(chunk.size(), static_cast<size_t>(PAYLOAD_SIZE * PACKETS_PER_URB));
            std::memcpy(ctx->buffer, chunk.data(), copyLen);
            if (copyLen < PAYLOAD_SIZE * PACKETS_PER_URB) {
                std::memset(ctx->buffer + copyLen, 0, PAYLOAD_SIZE * PACKETS_PER_URB - copyLen);
            }
        }

        if (ioctl(fd, USBDEVFS_SUBMITURB, ctx->urb) < 0) {
            ctx->inUse = false;
            usleep(1000);
        }
    }

    for (auto* u : urbPool) {
        freeUrbContext(u);
    }
    urbPool.clear();

    return nullptr;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_DueBoysenberry1226_ps5ctbro_audio_NativeAudioBridge_nativeIsoStreamStart(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jint fd, jint interfaceNumber, jint altSetting, jint endpointAddress) {

    if (fd < 0) return -1;
    if (g_streamRunning) return -2;

    g_streamFd = fd;
    g_streamInterfaceNumber = interfaceNumber;
    g_streamAltSetting = altSetting;
    g_streamEndpointAddress = endpointAddress;

    usbdevfs_setinterface setif{};
    setif.interface = interfaceNumber;
    setif.altsetting = altSetting;
    if (ioctl(fd, USBDEVFS_SETINTERFACE, &setif) < 0) {
        return -4;
    }

    g_streamRunning = true;

    {
        std::lock_guard<std::mutex> lock(g_pcmMutex);
        g_pcmQueue.clear();
    }

    pthread_create(&g_streamThread, nullptr, streamThreadFunction, nullptr);
    pthread_detach(g_streamThread);

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_DueBoysenberry1226_ps5ctbro_audio_NativeAudioBridge_nativeIsoPushPcm(
        JNIEnv* env, jobject /*thiz*/, jbyteArray pcmData) {

    if (!g_streamRunning) return -1;

    jsize len = env->GetArrayLength(pcmData);
    if (len <= 0) return -2;

    jbyte* data = env->GetByteArrayElements(pcmData, nullptr);
    if (!data) return -3;

    std::vector<unsigned char> chunk(len);
    std::memcpy(chunk.data(), data, len);
    env->ReleaseByteArrayElements(pcmData, data, JNI_ABORT);

    {
        std::lock_guard<std::mutex> lock(g_pcmMutex);
        // Megnöveltük 20-ra, hogy legyen tartalék a laggolás ellen
        if (g_pcmQueue.size() < 20) {
            g_pcmQueue.push_back(std::move(chunk));
        } else {
            g_pcmQueue.pop_front();
            g_pcmQueue.push_back(std::move(chunk));
        }
    }

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_DueBoysenberry1226_ps5ctbro_audio_NativeAudioBridge_nativeIsoStreamStop(
        JNIEnv* /*env*/, jobject /*thiz*/) {

    g_streamRunning = false;
    usleep(400000);

    g_streamFd = -1;
    g_streamInterfaceNumber = -1;
    g_streamAltSetting = -1;
    g_streamEndpointAddress = -1;

    {
        std::lock_guard<std::mutex> lock(g_pcmMutex);
        g_pcmQueue.clear();
    }

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_DueBoysenberry1226_ps5ctbro_audio_NativeAudioBridge_nativeUsbDeviceReset(
        JNIEnv* /*env*/, jobject /*thiz*/, jint fd) {

    if (fd < 0) return -1;

    int rc = ioctl(fd, USBDEVFS_RESET, 0);
    if (rc < 0) {
        return -errno;
    }

    return 0;
}