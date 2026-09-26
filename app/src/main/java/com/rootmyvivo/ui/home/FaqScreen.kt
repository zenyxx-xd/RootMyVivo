package com.rootmyvivo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R

/** Пункт FAQ: вопрос, ответ, опциональный копируемый шаблон. */
private data class FaqItem(val qRes: Int, val aRes: Int, val templateRes: Int = 0)

private data class FaqCategory(val titleRes: Int, val icon: ImageVector, val items: List<FaqItem>)

/**
 * FAQ приложения: инструкции, объяснения и разборы ошибок. Разбит на группы,
 * каждая карточка раскрывается по тапу; в карточке заявки — копируемый
 * шаблон обращения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onClose: () -> Unit) {
    val categories = listOf(
        FaqCategory(
            R.string.faq_cat_basics, Icons.Rounded.School,
            listOf(
                FaqItem(R.string.faq_q_root, R.string.faq_a_root),
                FaqItem(R.string.faq_q_status, R.string.faq_a_status),
                FaqItem(R.string.faq_q_how, R.string.faq_a_how),
                FaqItem(R.string.faq_q_manager, R.string.faq_a_manager),
                FaqItem(R.string.faq_q_reboot, R.string.faq_a_reboot),
            ),
        ),
        FaqCategory(
            R.string.faq_cat_errors, Icons.Rounded.WarningAmber,
            listOf(
                FaqItem(R.string.faq_q_deploy, R.string.faq_a_deploy),
                FaqItem(R.string.faq_q_notinstalled, R.string.faq_a_notinstalled),
                FaqItem(R.string.faq_q_exploitfail, R.string.faq_a_exploitfail),
                FaqItem(R.string.faq_q_appclosed, R.string.faq_a_appclosed),
            ),
        ),
        FaqCategory(
            R.string.faq_cat_guides, Icons.Rounded.MenuBook,
            listOf(
                FaqItem(R.string.faq_q_request, R.string.faq_a_request, R.string.faq_t_request),
                FaqItem(R.string.faq_q_report, R.string.faq_a_report),
                FaqItem(R.string.faq_q_downgrade, R.string.faq_a_downgrade),
                FaqItem(R.string.faq_q_firstlaunch, R.string.faq_a_firstlaunch),
            ),
        ),
        FaqCategory(
            R.string.faq_cat_advanced, Icons.Rounded.Science,
            listOf(
                FaqItem(R.string.faq_q_softreboot, R.string.faq_a_softreboot),
                FaqItem(R.string.faq_q_customso, R.string.faq_a_customso),
                FaqItem(R.string.faq_q_traces, R.string.faq_a_traces),
            ),
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_faq)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            var index = 0
            categories.forEach { cat ->
                // Каскад появления — как в карточках поддерживаемых устройств
                val myIndex = index++
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay((myIndex * 120L).coerceAtMost(480L))
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = expandVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(300, easing = FastOutSlowInEasing)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Icon(
                                cat.icon, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(cat.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        cat.items.forEach { item ->
                            FaqCard(item)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Раскрывающаяся карточка FAQ: вопрос + стрелка, внутри — ответ и шаблон. */
@Composable
private fun FaqCard(item: FaqItem) {
    var expanded by rememberSaveable(item.qRes) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 0f,
        animationSpec = tween(250),
        label = "faqChevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(item.qRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(240)),
                exit = androidx.compose.animation.shrinkVertically(tween(220)) +
                    androidx.compose.animation.fadeOut(tween(180)),
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    LinkifiedText(stringResource(item.aRes))
                    if (item.templateRes != 0) {
                        Spacer(Modifier.height(12.dp))
                        TemplateBox(item.templateRes)
                    }
                }
            }
        }
    }
}

/** Ответ FAQ: https-ссылки становятся кликабельными (открываются системой). */
private const val TAG_URL = "URL"

@Composable
private fun LinkifiedText(text: String) {
    val ctx = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text) {
        buildAnnotatedString {
            val re = Regex("""https?://\S+""")
            var last = 0
            for (m in re.findAll(text)) {
                val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '»', '"')
                append(text.substring(last, m.range.first))
                val start = length
                pushStringAnnotation(TAG_URL, url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                ) {
                    append(url)
                }
                pop()
                // хвостовая пунктуация, не вошедшая в ссылку
                append(text.substring(start + url.length, m.range.last + 1))
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    ClickableText(
        annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations(TAG_URL, offset, offset)
                .firstOrNull()
                ?.let { ann ->
                    try {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(ann.item),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(
                            ctx, ctx.getString(R.string.link_open_failed),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
        },
    )
}

/** Моноширинный блок шаблона с кнопкой копирования. */
@Composable
private fun TemplateBox(templateRes: Int) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val template = stringResource(templateRes)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                template,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(template))
                android.widget.Toast.makeText(
                    ctx, ctx.getString(R.string.log_copied), android.widget.Toast.LENGTH_SHORT,
                ).show()
            }) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    stringResource(R.string.copy_log),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
