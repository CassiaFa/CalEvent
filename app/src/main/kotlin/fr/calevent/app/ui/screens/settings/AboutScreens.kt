package fr.calevent.app.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.BuildConfig
import fr.calevent.app.R
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

/** About (9e). [onOpenIntro] replays the first-launch presentation. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenIntro: () -> Unit, onOpenPolicy: () -> Unit, onOpenLicenses: () -> Unit) {
    val c = CalEventTheme.colors
    ScreenScaffold(title = stringResource(R.string.settings_about_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(88.dp))
                Text(stringResource(R.string.settings_about_app), style = CalEventTheme.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = CalEventTheme.type.secondary, color = c.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.kept, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.settings_about_offline), style = CalEventTheme.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.kept)
                }
            }
            SettingsGroup {
                SettingsRow(stringResource(R.string.settings_about_intro), onClick = onOpenIntro)
                RowDivider()
                SettingsRow(stringResource(R.string.settings_about_policy), onClick = onOpenPolicy)
                RowDivider()
                SettingsRow(stringResource(R.string.settings_about_licenses), onClick = onOpenLicenses)
            }
        }
    }
}

/** Simple local text page. */
@Composable
private fun TextPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    ScreenScaffold(title = title, onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val c = CalEventTheme.colors
    TextPage(stringResource(R.string.settings_policy_title), onBack) {
        Text(stringResource(R.string.settings_policy_p1), style = CalEventTheme.type.body, color = c.ink)
        listOf(
            R.string.settings_policy_h2 to R.string.settings_policy_p2,
            R.string.settings_policy_h3 to R.string.settings_policy_p3,
            R.string.settings_policy_h4 to R.string.settings_policy_p4,
            R.string.settings_policy_h5 to R.string.settings_policy_p5,
        ).forEach { (h, p) ->
            Text(stringResource(h), style = CalEventTheme.type.bodyStrong, color = c.ink, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(stringResource(p), style = CalEventTheme.type.body, color = c.ink)
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = CalEventTheme.colors
    TextPage(stringResource(R.string.settings_licenses_title), onBack) {
        Text(stringResource(R.string.settings_licenses_intro), style = CalEventTheme.type.body, color = c.ink2)
        listOf(
            R.string.settings_license_androidx to R.string.settings_license_apache,
            R.string.settings_license_camerax to R.string.settings_license_apache,
            R.string.settings_license_kotlin to R.string.settings_license_apache,
            R.string.settings_license_mlkit to R.string.settings_license_mlkit_terms,
            R.string.settings_license_fonts to R.string.settings_license_ofl,
        ).forEach { (name, licence) ->
            Column(Modifier.padding(top = 12.dp)) {
                Text(stringResource(name), style = CalEventTheme.type.bodyStrong, color = c.ink)
                Text(stringResource(licence), style = CalEventTheme.type.secondary, color = c.ink2)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutLightPreview() = CalEventTheme(ThemeMode.LIGHT) { AboutScreen({}, {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutDarkPreview() = CalEventTheme(ThemeMode.DARK) { AboutScreen({}, {}, {}, {}) }
