package common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.kodein.di.compose.localDI
import org.kodein.di.instance
import repositories.ColorSchemeType
import repositories.ConfigurationRepository

@Composable
fun ColorSchemeConfigurationButton() {
    val configurationRepository by localDI().instance<ConfigurationRepository>()
    val configuration by configurationRepository.configurationFlow.collectAsState()
    val currentColorSchemeConfiguration = configuration.colorSchemeConfiguration
    var showDialog by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0) }
    IconButton(onClick = { showDialog = true }, modifier = Modifier.onSizeChanged { offset = it.height }) {
        Icon(currentColorSchemeConfiguration.type.icon, contentDescription = null)
    }
    if (showDialog) {
        Popup(
            onDismissRequest = { showDialog = false },
            properties = PopupProperties(focusable = true),
            offset = IntOffset(0, offset),
            alignment = Alignment.TopCenter,
        ) {
            Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                    for (colorSchemeType in ColorSchemeType.entries) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    val newColorSchemeConfiguration =
                                        configuration.colorSchemeConfiguration.copy(type = colorSchemeType)
                                    val newConfiguration =
                                        configuration.copy(colorSchemeConfiguration = newColorSchemeConfiguration)
                                    configurationRepository.updateConfiguration(newConfiguration)
                                    if (!dynamicColorSupported) {
                                        showDialog = false
                                    }
                                }
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(colorSchemeType.icon, contentDescription = null)
                            Text(colorSchemeType.name)
                        }
                    }
                    if (dynamicColorSupported) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Switch(
                                checked = configuration.colorSchemeConfiguration.useDynamic,
                                onCheckedChange = { useDynamic ->
                                    val newColorSchemeConfiguration =
                                        configuration.colorSchemeConfiguration.copy(useDynamic = useDynamic)
                                    val newConfiguration =
                                        configuration.copy(colorSchemeConfiguration = newColorSchemeConfiguration)
                                    configurationRepository.updateConfiguration(newConfiguration)
                                },
                            )
                            Text("Dynamic colors")
                        }
                    }
                }
            }
        }
    }
}

expect val dynamicColorSupported: Boolean

private val ColorSchemeType.icon: ImageVector
    get() = when (this) {
        ColorSchemeType.Light -> Icons.Default.LightMode
        ColorSchemeType.Dark -> Icons.Default.DarkMode
        ColorSchemeType.System -> Icons.Default.Contrast
    }
