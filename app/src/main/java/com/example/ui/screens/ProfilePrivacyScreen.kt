package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.BlockedUser
import com.example.data.model.EmergencyContact
import com.example.data.model.FriendRequestPrivacy
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.UiState
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

@Composable
fun ProfilePrivacyScreen(
    myProfileState: UiState<UserProfile?>,
    privacyState: UiState<PrivacySettings?>,
    blockedUsers: List<BlockedUser>,
    emergencyContacts: List<EmergencyContact> = emptyList(),
    onUpdateProfile: (displayName: String, username: String, bio: String, avatarUrl: String?) -> Unit,
    onUpdatePrivacy: (PrivacySettings) -> Unit,
    onUnblockUser: (String) -> Unit,
    onAddEmergencyContact: (name: String, phone: String, relationship: String) -> Unit = { _, _, _ -> },
    onDeleteEmergencyContact: (String) -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = (myProfileState as? UiState.Success)?.data
    val privacy = (privacyState as? UiState.Success)?.data

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var avatarData by remember(profile?.avatarUrl) { mutableStateOf<String?>(null) }

    var showBlockedDialog by remember { mutableStateOf(false) }
    var showAddEmergencyDialog by remember { mutableStateOf(false) }
    var sosContactName by remember { mutableStateOf("") }
    var sosContactPhone by remember { mutableStateOf("") }
    var sosContactRelation by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        if (profile != null) {
            displayName = profile.displayName
            username = profile.username
            bio = profile.bio
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                MediaUtils.compressImageToDataUri(context, it)?.let { compressed ->
                    avatarData = compressed
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_privacy_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- PROFILE CARD ---
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(
                            name = displayName.ifBlank { "Abhi" },
                            avatarUrl = avatarData ?: profile?.avatarUrl.orEmpty(),
                            size = 90.dp,
                            showOnlineDot = true
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change avatar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onUpdateProfile(displayName, username, bio, avatarData) },
                        enabled = displayName.isNotBlank() && username.length >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_profile_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Profile")
                    }
                }
            }
        }

        // --- PROFILE PRIVACY SETTINGS ---
        if (privacy != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Profile Privacy Settings", style = MaterialTheme.typography.titleLarge)
                        }

                        PrivacySelectorRow(
                            label = "Profile Visibility",
                            selected = privacy.profileVisibility,
                            onSelect = { onUpdatePrivacy(privacy.copy(profileVisibility = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Last Seen Privacy",
                            selected = privacy.lastSeenPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(lastSeenPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Online Status Privacy",
                            selected = privacy.onlineStatusPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(onlineStatusPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Profile Photo Privacy",
                            selected = privacy.profilePhotoPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(profilePhotoPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Bio Privacy",
                            selected = privacy.bioPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(bioPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Status Privacy",
                            selected = privacy.statusPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(statusPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Story Privacy",
                            selected = privacy.storyPrivacy,
                            onSelect = { onUpdatePrivacy(privacy.copy(storyPrivacy = it)) }
                        )
                        PrivacySelectorRow(
                            label = "Who Can Message Me",
                            selected = privacy.whoCanMessageMe,
                            onSelect = { onUpdatePrivacy(privacy.copy(whoCanMessageMe = it)) }
                        )

                        Column {
                            Text("Who Can Send Friend Requests", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FriendRequestPrivacy.entries.forEach { option ->
                                    FilterChip(
                                        selected = privacy.whoCanSendFriendRequests == option.name,
                                        onClick = { onUpdatePrivacy(privacy.copy(whoCanSendFriendRequests = option.name)) },
                                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- EMERGENCY SOS CONTACTS ---
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emergency_contacts_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = DangerRed)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Emergency SOS Contacts", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Pre-set numbers receive your live GPS location via SMS during an in-call SOS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (emergencyContacts.isEmpty()) {
                        Text(
                            text = "No emergency contacts configured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        emergencyContacts.forEachIndexed { idx, contact ->
                            if (idx > 0) HorizontalDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, style = MaterialTheme.typography.titleSmall)
                                    val sub = buildString {
                                        append(contact.phoneNumber)
                                        if (contact.relationship.isNotBlank()) {
                                            append(" • ${contact.relationship}")
                                        }
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { onDeleteEmergencyContact(contact.id) },
                                    modifier = Modifier.testTag("delete_emergency_contact_${contact.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove ${contact.name}",
                                        tint = DangerRed
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddEmergencyDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_emergency_contact_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Emergency Contact")
                    }
                }
            }
        }

        // --- BLOCKED CONTACTS MANAGEMENT ---
        item {
            OutlinedButton(
                onClick = { showBlockedDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("manage_blocked_users_button")
            ) {
                Icon(Icons.Default.Block, contentDescription = null, tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text("Blocked Contacts (${blockedUsers.size})")
            }
        }

        // --- SIGN OUT ---
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Contacts (${blockedUsers.size})") },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("You have not blocked anyone.")
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(blockedUsers, key = { it.id }) { blocked ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                UserAvatar(blocked.blockedName, blocked.blockedAvatarUrl, size = 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(blocked.blockedName, style = MaterialTheme.typography.titleSmall)
                                    if (blocked.reason.isNotBlank()) {
                                        Text(blocked.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { onUnblockUser(blocked.blockedId) }) {
                                    Text("Unblock", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Done") }
            }
        )
    }

    if (showAddEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showAddEmergencyDialog = false },
            title = { Text("Add Emergency Contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sosContactName,
                        onValueChange = { sosContactName = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_contact_name_input")
                    )
                    OutlinedTextField(
                        value = sosContactPhone,
                        onValueChange = { sosContactPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_contact_phone_input")
                    )
                    OutlinedTextField(
                        value = sosContactRelation,
                        onValueChange = { sosContactRelation = it },
                        label = { Text("Relationship (e.g. Mom, Partner)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_contact_relation_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEmergencyContact(
                            sosContactName.trim(),
                            sosContactPhone.trim(),
                            sosContactRelation.trim()
                        )
                        sosContactName = ""
                        sosContactPhone = ""
                        sosContactRelation = ""
                        showAddEmergencyDialog = false
                    },
                    enabled = sosContactName.isNotBlank() && sosContactPhone.filter { it.isDigit() || it == '+' }.length >= 3,
                    modifier = Modifier.testTag("save_emergency_contact_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEmergencyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PrivacySelectorRow(
    label: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrivacyLevel.entries.forEach { level ->
                FilterChip(
                    selected = selected == level.name,
                    onClick = { onSelect(level.name) },
                    label = { Text(level.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
