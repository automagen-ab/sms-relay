package ai.automagen.smsrelay.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A composable that provides an OutlinedTextField with syntax highlighting for JSON strings.
 * It also includes features for JSON validation and optional auto-formatting on focus loss.
 *
 * @param value The JSON string to be displayed and edited.
 * @param onValueChange The callback that is triggered when the input service updates the text.
 * @param modifier The modifier to be applied to this text field.
 * @param label A composable lambda for the text field's label. The label will indicate JSON validity.
 * @param placeholder A composable lambda for the text field's placeholder.
 * @param isError Whether the text field should be displayed in an error state. This is determined externally.
 * @param supportingText A composable lambda for displaying supporting text, such as error messages.
 * @param formatOnFocusLoss If true, the JSON input will be pretty-printed when the field loses focus.
 * @param onValidation A callback that reports whether the current JSON text is valid.
 */
@Composable
fun JsonHighlightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = { Text("JSON Body") },
    placeholder: @Composable (() -> Unit)? = { Text("{\n  \"message\": \"{sms_body}\",\n  ...\n}") },
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    formatOnFocusLoss: Boolean = true,
    onValidation: (Boolean) -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var hasFocus by remember { mutableStateOf(false) }

    // Determines if the current JSON text is valid.
    val isJsonValid by remember(textFieldValue.text) {
        derivedStateOf {
            if (textFieldValue.text.isBlank()) true // Allow empty state
            else runCatching { jsonParser.parseToJsonElement(textFieldValue.text) }.isSuccess
        }
    }

    // Propagate validation status to the parent composable.
    LaunchedEffect(isJsonValid) {
        onValidation(isJsonValid)
    }

    // Syncs the internal state with the external `value` prop.
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    val visualTransformation = remember { JsonVisualTransformation() }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            // Propagate the change to the external state holder.
            if (value != it.text) {
                onValueChange(it.text)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 200.dp)
            .onFocusChanged { focusState ->
                hasFocus = focusState.isFocused
                if (!hasFocus && formatOnFocusLoss && isJsonValid && textFieldValue.text.isNotBlank()) {
                    val formatted = formatJson(textFieldValue.text)
                    if (formatted != textFieldValue.text) {
                        onValueChange(formatted)
                    }
                }
            },
        label = label,
        placeholder = placeholder,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = visualTransformation,
        interactionSource = remember { MutableInteractionSource() },
        isError = isError,
        supportingText = supportingText,
        singleLine = false
    )
}

/**
 * A VisualTransformation that applies syntax highlighting to a JSON string.
 */
private class JsonVisualTransformation(private val colors: Map<String, Color> = defaultColors) :
    VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedString { highlightJson(text.text, colors) },
            OffsetMapping.Identity
        )
    }
}

/**
 * A lenient JSON parser instance for validation and formatting.
 */
private val jsonParser = Json {
    isLenient = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Formats a given JSON string into a pretty-printed version.
 * Returns the original string if formatting fails.
 */
private fun formatJson(json: String): String {
    return try {
        val jsonElement = jsonParser.parseToJsonElement(json)
        jsonParser.encodeToString(jsonElement)
    } catch (e: Exception) {
        json // Return original string if it's not valid JSON
    }
}

/**
 * Default color scheme for JSON syntax highlighting.
 */
private val defaultColors = mapOf(
    "key" to Color(0xFF1565C0),     // Blue
    "string" to Color(0xFF2E7D32),  // Green
    "number" to Color(0xFFEF6C00),  // Orange
    "boolean" to Color(0xFFD32F2F), // Red
    "punct" to Color(0xFF757575)    // Gray
)

/**
 * Analyzes a JSON string and returns an AnnotatedString with syntax highlighting.
 */
private fun AnnotatedString.Builder.highlightJson(json: String, colors: Map<String, Color>) {
    val regex = Regex(
        """("(\\.|[^"\\])*"(?=\s*:))|("(\\.|[^"\\])*")|(\b\d+(\.\d+)?\b)|(\btrue\b|\bfalse\b|\bnull\b)|([{}\[\]:,])"""
    )
    var lastIndex = 0

    for (match in regex.findAll(json)) {
        // Append un-styled text (whitespace) between matches.
        if (match.range.first > lastIndex) {
            append(json.substring(lastIndex, match.range.first))
        }

        val part = match.value
        // Determine the style for the matched token.
        val color = when {
            // Key (a string followed by a colon)
            part.startsWith('"') && json.drop(match.range.last + 1).trimStart()
                .startsWith(':') -> colors["key"]

            part.startsWith('"') -> colors["string"]
            part.matches(Regex("""\\b\\d+(\\.\d+)?\\b""")) -> colors["number"]
            part.matches(Regex("\\btrue\b|\\bfalse\b|\\bnull\b")) -> colors["boolean"]
            else -> colors["punct"]
        } ?: Color.Unspecified

        withStyle(SpanStyle(color = color)) {
            append(part)
        }

        lastIndex = match.range.last + 1
    }

    // Append any remaining text after the last match.
    if (lastIndex < json.length) {
        append(json.substring(lastIndex))
    }
}
