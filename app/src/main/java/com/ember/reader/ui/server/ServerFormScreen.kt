package com.ember.reader.ui.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.grimmory.GrimmoryUserPermissions
import com.ember.reader.ui.settings.components.PermissionChip
import com.ember.reader.ui.settings.components.SettingsCard
import com.ember.reader.ui.settings.components.SettingsLabel
import com.ember.reader.ui.settings.components.SettingsScreenHeader
import com.ember.reader.ui.theme.ServerAccentPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerFormScreen(
    serverId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: ServerFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Server type picker state — skip if editing
    var serverTypeChosen by rememberSaveable { mutableStateOf(uiState.isEditing) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isGrimmoryType by rememberSaveable { mutableStateOf(uiState.isGrimmory) }

    // For Grimmory: use Grimmory login for OPDS/Kosync? Default ON for convenience
    var useGrimmoryLoginForOpds by rememberSaveable { mutableStateOf(true) }
    var useGrimmoryLoginForKosync by rememberSaveable { mutableStateOf(true) }

    // Update when editing state loads
    if (uiState.isEditing && !serverTypeChosen) {
        serverTypeChosen = true
        isGrimmoryType = uiState.isGrimmory
        // If OPDS/Kosync credentials differ from Grimmory, uncheck the boxes
        val hasCustomOpds = uiState.opdsUsername.isNotBlank() &&
            uiState.opdsUsername != uiState.grimmoryUsername
        val hasCustomKosync = uiState.kosyncUsername.isNotBlank() &&
            uiState.kosyncUsername != uiState.grimmoryUsername
        useGrimmoryLoginForOpds = !hasCustomOpds
        useGrimmoryLoginForKosync = !hasCustomKosync
    }

    Scaffold(
        topBar = {
            // The long titles ("Add Grimmory Server") do not fit the large header, so the
            // overline carries what is being done and the title what it is done to.
            val adding = stringResource(R.string.add_server)
            val (overline, title) = when {
                uiState.isEditing -> stringResource(R.string.servers_overline) to stringResource(R.string.edit_server)
                !serverTypeChosen -> stringResource(R.string.servers_overline) to adding
                isGrimmoryType -> adding to stringResource(R.string.grimmory)
                else -> adding to stringResource(R.string.opds_short)
            }
            SettingsScreenHeader(
                overline = overline,
                title = title,
                onNavigateBack = {
                    if (serverTypeChosen && !uiState.isEditing) {
                        serverTypeChosen = false
                    } else {
                        onNavigateBack()
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !uiState.isSaving) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete server")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete server?") },
                text = {
                    Text(
                        "\"${uiState.name}\" and its sign-in details will be removed from Ember. " +
                            "Books you downloaded from it stay in your Library."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.delete(onNavigateBack)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (!serverTypeChosen) {
            // Step 1: Choose server type
            ServerTypePicker(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                onGrimmory = {
                    isGrimmoryType = true
                    serverTypeChosen = true
                    // Both off by default on new Grimmory servers (user opts in explicitly)
                    viewModel.setOpdsEnabled(false)
                    viewModel.setKosyncEnabled(false)
                },
                onOpds = {
                    isGrimmoryType = false
                    serverTypeChosen = true
                    // Plain OPDS servers are pointless without OPDS — on by default.
                    // Kosync still opt-in.
                    viewModel.setOpdsEnabled(true)
                    viewModel.setKosyncEnabled(false)
                }
            )
        } else if (isGrimmoryType) {
            // Step 2a: Grimmory form
            GrimmoryForm(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                uiState = uiState,
                useGrimmoryLoginForOpds = useGrimmoryLoginForOpds,
                onUseGrimmoryLoginForOpdsChanged = { useGrimmoryLoginForOpds = it },
                useGrimmoryLoginForKosync = useGrimmoryLoginForKosync,
                onUseGrimmoryLoginForKosyncChanged = { useGrimmoryLoginForKosync = it },
                viewModel = viewModel,
                onSave = {
                    viewModel.saveGrimmory(
                        useGrimmoryLoginForOpds = useGrimmoryLoginForOpds,
                        useGrimmoryLoginForKosync = useGrimmoryLoginForKosync,
                        onSuccess = onNavigateBack
                    )
                }
            )
        } else {
            // Step 2b: Generic OPDS form
            OpdsForm(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                uiState = uiState,
                viewModel = viewModel,
                onSave = { viewModel.save(onNavigateBack) }
            )
        }
    }
}

@Composable
private fun ServerTypePicker(
    modifier: Modifier = Modifier,
    onGrimmory: () -> Unit,
    onOpds: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingsLabel(
            text = stringResource(R.string.what_type_of_server),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGrimmory)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.grimmory),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.grimmory_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpds)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.other_opds_server),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.opds_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GrimmoryForm(
    modifier: Modifier = Modifier,
    uiState: ServerFormUiState,
    useGrimmoryLoginForOpds: Boolean,
    onUseGrimmoryLoginForOpdsChanged: (Boolean) -> Unit,
    useGrimmoryLoginForKosync: Boolean,
    onUseGrimmoryLoginForKosyncChanged: (Boolean) -> Unit,
    viewModel: ServerFormViewModel,
    onSave: () -> Unit
) {
    Column(modifier = modifier) {
        // Server info
        SectionHeader(icon = Icons.Default.Public, title = stringResource(R.string.server_section))
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::updateName,
            label = { Text(stringResource(R.string.server_name)) },
            placeholder = { Text(stringResource(R.string.placeholder_grimmory_name)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.url,
            onValueChange = viewModel::updateUrl,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.placeholder_grimmory_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Grimmory login (primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(icon = Icons.Default.Lock, title = stringResource(R.string.grimmory_login))
            Spacer(modifier = Modifier.width(8.dp))
            EncryptedBadge()
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.grimmoryUsername,
            onValueChange = viewModel::updateGrimmoryUsername,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.grimmoryPassword,
            onValueChange = viewModel::updateGrimmoryPassword,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TestConnectionButton(
            result = uiState.grimmoryTestResult,
            onClick = viewModel::testGrimmoryConnection,
            label = stringResource(R.string.test_login)
        )

        AnimatedVisibility(visible = uiState.grimmoryTestPermissions != null) {
            uiState.grimmoryTestPermissions?.let {
                Spacer(modifier = Modifier.height(8.dp))
                TestPermissionsPanel(it)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // OPDS section
        SectionHeader(icon = Icons.Default.CloudQueue, title = stringResource(R.string.opds_credentials))
        Spacer(modifier = Modifier.height(8.dp))
        EnableSectionCheckbox(
            checked = uiState.opdsEnabled,
            onCheckedChange = viewModel::setOpdsEnabled,
            label = stringResource(R.string.enable_opds)
        )
        AnimatedVisibility(visible = uiState.opdsEnabled) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                SameLoginCheckbox(
                    checked = useGrimmoryLoginForOpds,
                    onCheckedChange = onUseGrimmoryLoginForOpdsChanged,
                    label = stringResource(R.string.use_grimmory_login_for_opds)
                )
                AnimatedVisibility(visible = !useGrimmoryLoginForOpds) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.opdsUsername,
                            onValueChange = viewModel::updateOpdsUsername,
                            label = { Text(stringResource(R.string.opds_username)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.opdsPassword,
                            onValueChange = viewModel::updateOpdsPassword,
                            label = { Text(stringResource(R.string.opds_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TestConnectionButton(
                    result = uiState.opdsTestResult,
                    onClick = {
                        if (useGrimmoryLoginForOpds) {
                            viewModel.testOpdsWithGrimmoryCredentials()
                        } else {
                            viewModel.testOpdsConnection()
                        }
                    },
                    label = stringResource(R.string.test_opds)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Kosync section
        SectionHeader(icon = Icons.Default.Sync, title = stringResource(R.string.kosync_credentials))
        Text(
            text = stringResource(R.string.kosync_sync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        EnableSectionCheckbox(
            checked = uiState.kosyncEnabled,
            onCheckedChange = viewModel::setKosyncEnabled,
            label = stringResource(R.string.enable_kosync)
        )
        AnimatedVisibility(visible = uiState.kosyncEnabled) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                SameLoginCheckbox(
                    checked = useGrimmoryLoginForKosync,
                    onCheckedChange = onUseGrimmoryLoginForKosyncChanged,
                    label = stringResource(R.string.use_grimmory_login_for_kosync)
                )
                AnimatedVisibility(visible = !useGrimmoryLoginForKosync) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.kosyncUsername,
                            onValueChange = viewModel::updateKosyncUsername,
                            label = { Text(stringResource(R.string.kosync_username)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.kosyncPassword,
                            onValueChange = viewModel::updateKosyncPassword,
                            label = { Text(stringResource(R.string.kosync_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TestConnectionButton(
                    result = uiState.kosyncTestResult,
                    onClick = {
                        if (useGrimmoryLoginForKosync) {
                            viewModel.testKosyncWithGrimmoryCredentials()
                        } else {
                            viewModel.testKosyncConnection()
                        }
                    },
                    label = stringResource(R.string.test_kosync)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AppearanceSection(
            currentSlot = uiState.accentColorSlot,
            onSelect = viewModel::updateAccentColorSlot
        )

        uiState.validationError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (uiState.isEditing) stringResource(R.string.update_server) else stringResource(R.string.save_server),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun OpdsForm(
    modifier: Modifier = Modifier,
    uiState: ServerFormUiState,
    viewModel: ServerFormViewModel,
    onSave: () -> Unit
) {
    Column(modifier = modifier) {
        SectionHeader(icon = Icons.Default.CloudQueue, title = stringResource(R.string.server_section))
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::updateName,
            label = { Text(stringResource(R.string.server_name)) },
            placeholder = { Text(stringResource(R.string.placeholder_opds_name)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.url,
            onValueChange = viewModel::updateUrl,
            label = { Text(stringResource(R.string.opds_url)) },
            placeholder = { Text(stringResource(R.string.placeholder_opds_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // OPDS Credentials
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(icon = Icons.Default.Lock, title = stringResource(R.string.opds_credentials))
            Spacer(modifier = Modifier.width(8.dp))
            EncryptedBadge()
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.opdsUsername,
            onValueChange = viewModel::updateOpdsUsername,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.opdsPassword,
            onValueChange = viewModel::updateOpdsPassword,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TestConnectionButton(
            result = uiState.opdsTestResult,
            onClick = viewModel::testOpdsConnection,
            label = stringResource(R.string.test_opds)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Kosync (optional)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(icon = Icons.Default.Sync, title = stringResource(R.string.kosync_credentials))
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text(
                    text = stringResource(R.string.optional_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.kosync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        EnableSectionCheckbox(
            checked = uiState.kosyncEnabled,
            onCheckedChange = viewModel::setKosyncEnabled,
            label = stringResource(R.string.enable_kosync)
        )
        AnimatedVisibility(visible = uiState.kosyncEnabled) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.kosyncUsername,
                    onValueChange = viewModel::updateKosyncUsername,
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.kosyncPassword,
                    onValueChange = viewModel::updateKosyncPassword,
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TestConnectionButton(
                    result = uiState.kosyncTestResult,
                    onClick = viewModel::testKosyncConnection,
                    label = stringResource(R.string.test_kosync)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AppearanceSection(
            currentSlot = uiState.accentColorSlot,
            onSelect = viewModel::updateAccentColorSlot
        )

        uiState.validationError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (uiState.isEditing) stringResource(R.string.update_server) else stringResource(R.string.save_server),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AppearanceSection(currentSlot: Int?, onSelect: (Int?) -> Unit) {
    SectionHeader(icon = Icons.Default.Palette, title = stringResource(R.string.appearance))
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        ServerAccentPalette.forEachIndexed { index, color ->
            val selected = currentSlot == index
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
    if (currentSlot != null) {
        TextButton(onClick = { onSelect(null) }) {
            Text(stringResource(R.string.reset_to_default))
        }
    }
}

@Composable
private fun EncryptedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text = stringResource(R.string.encrypted_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SameLoginCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EnableSectionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TestConnectionButton(result: TestResult, onClick: () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onClick,
            enabled = result !is TestResult.Testing,
            shape = RoundedCornerShape(10.dp)
        ) {
            if (result is TestResult.Testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(label)
            if (result is TestResult.Success) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (result is TestResult.Error) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestPermissionsPanel(permissions: GrimmoryUserPermissions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.permissions_on_this_server),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PermissionChip("Admin", permissions.isAdmin)
            PermissionChip("Download", permissions.canDownload)
            PermissionChip("Upload", permissions.canUpload)
            PermissionChip("Bookdrop", permissions.canAccessBookdrop)
            PermissionChip("Organize Files", permissions.canMoveOrganizeFiles)
        }
    }
}
