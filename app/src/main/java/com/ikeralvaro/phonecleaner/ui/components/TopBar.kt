package com.ikeralvaro.phonecleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.ui.icons.AppIcons
import com.ikeralvaro.phonecleaner.ui.theme.brandGradient

/** Alto que ocupa la barra inferior "píldora" (incluida la barra de navegación del sistema); 0 fuera de las pestañas. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * Cabecera con el degradado del logo y esquinas inferiores redondeadas (como la de Inicio).
 * Llega por debajo de la barra de estado. [content] permite meter debajo del título un medidor grande.
 */
@Composable
fun GradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(brandGradient())
            .statusBarsPadding()
            .padding(bottom = if (content != null) 20.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .semantics { heading() },
                )
                actions()
            }
            if (content != null) content()
        }
    }
}

/** Relleno inferior para el contenido desplazable: barra píldora, barra del sistema o botón inferior (el mayor). */
@Composable
fun bottomContentPadding(scaffoldBottom: Dp = 0.dp): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(scaffoldBottom, nav, LocalBottomBarPadding.current)
}

/** Relleno de una lista dentro de un Scaffold con [GradientHeader] (el relleno va dentro del desplazamiento). */
@Composable
fun listPadding(scaffold: PaddingValues, horizontal: Dp = 16.dp, vertical: Dp = 16.dp): PaddingValues = PaddingValues(
    start = horizontal,
    end = horizontal,
    top = scaffold.calculateTopPadding() + vertical,
    bottom = bottomContentPadding(scaffold.calculateBottomPadding()) + vertical,
)

/** Zona inferior fija con el botón principal de la pantalla. */
@Composable
fun BottomAction(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
        ) { content() }
    }
}
