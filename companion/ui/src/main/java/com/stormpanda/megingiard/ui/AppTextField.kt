package com.stormpanda.megingiard.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.stormpanda.megingiard.AppLog

private const val TAG = "AppTextField"

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    OutlinedTextField(
        value = value,
        onValueChange = {
            AppLog.d(TAG, "AppTextField value changed: len=${it.length}, isError=$isError")
            onValueChange(it)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = colors.subduedBorder,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                cursorColor = accentColor,
                errorBorderColor = colors.error,
                errorCursorColor = colors.error,
            ),
    )
}

@Composable
internal fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalAppColors.current
    val accentColor = colors.accent
    OutlinedTextField(
        value = value,
        onValueChange = {
            AppLog.d(TAG, "AppTextField value changed: len=${it.text.length}, isError=$isError")
            onValueChange(it)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = colors.subduedBorder,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                cursorColor = accentColor,
                errorBorderColor = colors.error,
                errorCursorColor = colors.error,
            ),
    )
}
