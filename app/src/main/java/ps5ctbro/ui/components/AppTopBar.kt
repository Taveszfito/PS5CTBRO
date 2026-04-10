package com.DueBoysenberry1226.ps5ctbro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.ui.AppSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    )
}

@Composable
fun AppDrawerContent(
    currentSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onCloseClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val drawerWidth = configuration.screenWidthDp.dp * 0.75f
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onCloseClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Close"
                    )
                }

                Text(
                    text = currentSection.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            DrawerMenuItem(
                title = AppSection.SPEAKER.title,
                selected = currentSection == AppSection.SPEAKER,
                onClick = { onSectionSelected(AppSection.SPEAKER) }
            )

            DrawerMenuItem(
                title = AppSection.ADAPTIVE_TRIGGERS.title,
                selected = currentSection == AppSection.ADAPTIVE_TRIGGERS,
                onClick = { onSectionSelected(AppSection.ADAPTIVE_TRIGGERS) }
            )

            DrawerMenuItem(
                title = AppSection.LEDS.title,
                selected = currentSection == AppSection.LEDS,
                onClick = { onSectionSelected(AppSection.LEDS) }
            )
            DrawerMenuItem(
                title = AppSection.INPUT_TEST.title,
                selected = currentSection == AppSection.INPUT_TEST,
                onClick = { onSectionSelected(AppSection.INPUT_TEST) }
            )

            DrawerMenuItem(
                title = AppSection.SETTINGS.title,
                selected = currentSection == AppSection.SETTINGS,
                onClick = { onSectionSelected(AppSection.SETTINGS) }
            )

            DrawerMenuItem(
                title = AppSection.DEBUG.title,
                selected = currentSection == AppSection.DEBUG,
                onClick = { onSectionSelected(AppSection.DEBUG) }
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}