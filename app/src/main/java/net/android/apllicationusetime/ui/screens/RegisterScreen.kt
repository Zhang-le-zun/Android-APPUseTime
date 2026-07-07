package net.android.apllicationusetime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RegisterScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onRegister: (email: String, password: String, username: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val displayError = localError ?: errorMessage

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / 标题
            Text(
                text = "📱",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "创建账号",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "注册后即可同步你的使用数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 错误消息
            if (displayError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 用户名输入
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; localError = null },
                label = { Text("用户名") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 邮箱输入
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("邮箱") },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 密码输入
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 确认密码输入
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null },
                label = { Text("确认密码") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && email.isNotBlank()
                            && password.isNotBlank() && password == confirmPassword) {
                            onRegister(email.trim(), password, username.trim())
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 注册按钮
            Button(
                onClick = {
                    focusManager.clearFocus()
                    localError = null
                    if (username.isBlank()) {
                        localError = "请输入用户名"
                    } else if (email.isBlank()) {
                        localError = "请输入邮箱"
                    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        localError = "请输入有效的邮箱地址"
                    } else if (password.length < 6) {
                        localError = "密码至少需要6位"
                    } else if (password != confirmPassword) {
                        localError = "两次密码不一致"
                    } else {
                        onRegister(email.trim(), password, username.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && username.isNotBlank() && email.isNotBlank()
                        && password.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("注册", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录导航
            Text(
                text = "已有账号？点击登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !isLoading) { onNavigateToLogin() }
            )
        }
    }
}