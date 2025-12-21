package com.lelloman.androidoscopy.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import kotlinx.coroutines.launch

@Composable
fun ButtonWidget(
    label: String,
    action: String,
    style: String,
    onAction: suspend (String, Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    when (style.lowercase()) {
        "primary" -> {
            Button(
                onClick = { scope.launch { onAction(action, emptyMap()) } },
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DashboardColors.Primary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = label)
            }
        }
        "danger" -> {
            Button(
                onClick = { scope.launch { onAction(action, emptyMap()) } },
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DashboardColors.Danger,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = label)
            }
        }
        else -> { // secondary
            OutlinedButton(
                onClick = { scope.launch { onAction(action, emptyMap()) } },
                modifier = modifier,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DashboardColors.TextPrimary
                ),
                border = BorderStroke(1.dp, DashboardColors.Border),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = label)
            }
        }
    }
}
