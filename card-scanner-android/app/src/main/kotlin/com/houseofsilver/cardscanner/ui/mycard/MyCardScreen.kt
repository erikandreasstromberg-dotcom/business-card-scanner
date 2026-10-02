package com.houseofsilver.cardscanner.ui.mycard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.houseofsilver.cardscanner.R
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.qr.QrEncoder
import com.houseofsilver.cardscanner.qr.VCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCardScreen(
    appModule: AppModule,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: MyCardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyCardViewModel(appModule) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { vm.onImagePicked(context, it) } }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCaptureFile?.let { vm.onImageCaptured(it) }
        pendingCaptureFile = null
    }

    val contact = state.toContact()
    val qrBitmap = remember(contact) {
        if (VCard.isUsable(contact)) QrEncoder.encode(VCard.build(contact)) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_card_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.my_card_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader(stringResource(R.string.my_card_details))
            Field("Name", state.name, vm::onNameChange)
            Field("Email", state.email, vm::onEmailChange, KeyboardType.Email)
            Field("Company", state.company, vm::onCompanyChange)
            Field("Role / Title", state.role, vm::onRoleChange)
            Field("Phone", state.phone, vm::onPhoneChange, KeyboardType.Phone)
            Field("Website", state.website, vm::onWebsiteChange, KeyboardType.Uri)
            Field("LinkedIn", state.linkedin, vm::onLinkedinChange)

            SectionHeader(stringResource(R.string.my_card_qr))
            if (qrBitmap != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.my_card_qr),
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White)
                                .padding(8.dp)
                        )
                        Text(
                            stringResource(R.string.my_card_qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.my_card_qr_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeader(stringResource(R.string.my_card_image))
            Text(
                stringResource(R.string.my_card_image_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.imageUri?.let { uriStr ->
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = stringResource(R.string.my_card_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.FillWidth
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.my_card_choose))
                }
                OutlinedButton(
                    onClick = {
                        val file = vm.newCaptureFile(context)
                        pendingCaptureFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        takePhoto.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.my_card_photo))
                }
            }

            if (state.imageUri != null) {
                TextButton(
                    onClick = { vm.clearImage() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.my_card_remove))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
