package com.DueBoysenberry1226.ps5ctbro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
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
    onCloseClick: () -> Unit   // ✅ ÚJ
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
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                )
        ) {

            // ✅ Drawer HEADER (hamburger + cím)
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

                Spacer(modifier = Modifier.width(8.dp))

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
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}