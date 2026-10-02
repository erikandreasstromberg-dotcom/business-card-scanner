package com.houseofsilver.cardscanner.ui.mycard

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.houseofsilver.cardscanner.R
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.qr.QrEncoder
import com.houseofsilver.cardscanner.qr.VCard

/**
 * Shows the user's own card so someone opposite can capture it.
 *
 * The QR is a vCard, so it works with any phone's built-in camera — the other person doesn't
 * need this app. Screen brightness is forced to maximum and the display kept awake, because a
 * dimmed or sleeping screen is the usual reason a scan fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardScreen(
    appModule: AppModule,
    onNavigateBack: () -> Unit,
    onNavigateToMyCard: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = previousBrightness
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val contact = remember { appModule.ownCardPrefs.getContact() }
    val imageUri = remember { appModule.ownCardPrefs.getImageUri() }
    val qrBitmap = remember(contact) {
        if (VCard.isUsable(contact)) QrEncoder.encode(VCard.build(contact)) else null
    }
    val hasAnything = qrBitmap != null || imageUri != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_card_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasAnything) {
                Spacer(Modifier.height(48.dp))
                Text(
                    stringResource(R.string.share_card_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onNavigateToMyCard) {
                    Text(stringResource(R.string.share_card_setup))
                }
                return@Column
            }

            qrBitmap?.let { bitmap ->
                // White plate around the QR: scanners need the quiet zone and the contrast,
                // and the app's own theme is dark.
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.my_card_qr),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp)
                    )
                }

                if (contact.name.isNotBlank()) {
                    Text(contact.name, style = MaterialTheme.typography.headlineSmall)
                }
                listOfNotNull(contact.role, contact.company)
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            it.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                Text(
                    stringResource(R.string.share_card_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            imageUri?.let { uriStr ->
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = stringResource(R.string.my_card_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White),
                    contentScale = ContentScale.FillWidth
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
