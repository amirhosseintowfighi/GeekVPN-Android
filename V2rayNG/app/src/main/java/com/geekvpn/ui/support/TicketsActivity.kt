package com.geekvpn.ui.support

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.api.TicketCard
import com.geekvpn.api.TicketMessage
import com.geekvpn.support.TicketStatus
import com.geekvpn.support.TicketTopic
import com.geekvpn.support.Tickets
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDateTime
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.geekPage
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

/**
 * "تیکت‌های من": the customer's support tickets, support's answers, replies
 * and new tickets, over the Mini App's API. The operator answers from the
 * panel or the bot as before.
 */
class TicketsActivity : BaseComponentActivity() {
    private val viewModel: TicketsViewModel by viewModels { TicketsViewModel.factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pollWhileVisible() }
                viewModel.events.collect { event ->
                    val text = when (event) {
                        is TicketsEvent.Failed -> event.serverText ?: getString(event.message)
                        is TicketsEvent.Opened -> event.reference?.let { getString(R.string.geek_tickets_opened_ref, it) }
                            ?: getString(R.string.geek_tickets_opened)
                    }
                    Toast.makeText(this@TicketsActivity, text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BackHandler(enabled = state.page != TicketsPage.List) { viewModel.back() }
            GeekBackdrop {
                TicketsScreen(state, TicketsViewActions(viewModel) { if (!viewModel.back()) finish() })
            }
        }
    }
}

/** What the screen can ask for; the activity wires it to [TicketsViewModel]. */
interface TicketsActions {
    fun onBack()
    fun onRetry()
    fun onOpen(ticketId: String)
    fun onNew()
    fun onReply(text: String)
    fun onSendReply()
    fun onTopic(topic: TicketTopic)
    fun onSubject(text: String)
    fun onBody(text: String)
    fun onSubmit()
}

private class TicketsViewActions(private val vm: TicketsViewModel, private val back: () -> Unit) : TicketsActions {
    override fun onBack() = back()
    override fun onRetry() = vm.refresh()
    override fun onOpen(ticketId: String) = vm.open(ticketId)
    override fun onNew() = vm.startNew()
    override fun onReply(text: String) = vm.setReply(text)
    override fun onSendReply() = vm.sendReply()
    override fun onTopic(topic: TicketTopic) = vm.setTopic(topic)
    override fun onSubject(text: String) = vm.setSubject(text)
    override fun onBody(text: String) = vm.setBody(text)
    override fun onSubmit() = vm.submitNew()
}

@Composable
fun TicketsScreen(state: TicketsUiState, actions: TicketsActions) {
    val colors = Geek.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val title = when (state.page) {
        TicketsPage.List -> stringResource(R.string.geek_tickets_title)
        TicketsPage.New -> stringResource(R.string.geek_tickets_new)
        is TicketsPage.Thread -> state.openTicket?.topicFa?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geek_tickets_thread)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), actions::onBack)
            Text(
                title,
                style = Geek.type.pageTitle.copy(fontSize = 22.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.page == TicketsPage.List && state.signedIn) {
                GlassIconButton(GeekIcons.Plus, stringResource(R.string.geek_tickets_new), actions::onNew)
            }
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(
                targetState = state.page,
                transitionSpec = { geekPage(forward = targetState != TicketsPage.List, rtl = rtl) },
                contentKey = { it::class },
                label = "tickets-page",
            ) { page ->
                when (page) {
                    TicketsPage.List -> TicketList(state, actions)
                    is TicketsPage.Thread -> TicketThread(state, actions)
                    TicketsPage.New -> NewTicket(state, actions)
                }
            }
        }
    }
}

@Composable
private fun TicketList(state: TicketsUiState, actions: TicketsActions) {
    val colors = Geek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tickets = state.tickets
        when {
            !state.signedIn -> Text(stringResource(R.string.geek_tickets_guest), style = Geek.type.body, color = colors.onGlassMuted)
            tickets == null && state.listFailed -> {
                Text(stringResource(R.string.geek_tickets_err_load), style = Geek.type.body, color = colors.onGlassMuted)
                GeekSecondaryButton(stringResource(R.string.geek_tickets_retry), actions::onRetry, Modifier.fillMaxWidth(), icon = GeekIcons.Refresh)
            }
            tickets == null -> Text(stringResource(R.string.geek_tickets_loading), style = Geek.type.body, color = colors.onGlassMuted)
            tickets.isEmpty() -> {
                Text(stringResource(R.string.geek_tickets_empty), style = Geek.type.body, color = colors.onGlassMuted)
                GeekPrimaryButton(stringResource(R.string.geek_tickets_new), GeekIcons.Plus, actions::onNew)
            }
            else -> {
                Text(stringResource(R.string.geek_tickets_intro), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                tickets.forEach { ticket -> TicketRow(ticket) { ticket.ticketId?.let(actions::onOpen) } }
            }
        }
    }
}

@Composable
private fun TicketRow(ticket: TicketCard, onClick: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val status = TicketStatus.of(ticket.state)
    val unread = (ticket.unreadCount ?: 0).coerceAtLeast(0)
    GlassSurface(
        kind = GlassKind.Milk,
        shape = Geek.shapes.card,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ticket.topicFa?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geek_tickets_thread),
                    style = Geek.type.row,
                    color = colors.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(status)
            }
            ticket.lastMessageFa?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = Geek.type.caption.copy(textDirection = TextDirection.Content),
                    color = colors.onGlassMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(ticket.reference, formatDateTime(ticket.lastReplyAt ?: ticket.createdAt, locale)).joinToString(" · "),
                    style = Geek.type.micro,
                    color = colors.onGlassMuted,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Text(
                        stringResource(R.string.geek_tickets_unread, formatNumber(unread.toLong(), locale)),
                        style = Geek.type.micro,
                        color = colors.onAction,
                        modifier = Modifier.clip(Geek.shapes.pill).background(colors.action).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: TicketStatus) {
    val colors = Geek.colors
    val (background, foreground) = when (status) {
        TicketStatus.Waiting -> colors.warningSoft to colors.warning
        TicketStatus.Answered -> colors.successSoft to colors.success
        TicketStatus.Open -> colors.soft to colors.onGlass
        TicketStatus.Closed -> colors.soft to colors.onGlassMuted
    }
    Text(
        stringResource(status.label),
        style = Geek.type.micro,
        color = foreground,
        modifier = Modifier.clip(Geek.shapes.pill).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TicketThread(state: TicketsUiState, actions: TicketsActions) {
    val colors = Geek.colors
    val scroll = rememberScrollState()
    val messages = state.messages
    val status = TicketStatus.of(state.openTicket?.state)
    // The newest message is at the bottom, like every chat.
    LaunchedEffect(messages?.size) { if (messages != null) scroll.animateScrollTo(scroll.maxValue) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.openTicket?.let { ticket ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ticket.reference.orEmpty(), style = Geek.type.caption, color = colors.onGlassMuted, modifier = Modifier.weight(1f))
                StatusChip(status)
            }
        }
        when {
            messages == null && state.threadFailed -> {
                Text(stringResource(R.string.geek_tickets_err_load), style = Geek.type.body, color = colors.onGlassMuted)
                GeekSecondaryButton(stringResource(R.string.geek_tickets_retry), actions::onRetry, Modifier.fillMaxWidth(), icon = GeekIcons.Refresh)
            }
            messages == null -> Text(stringResource(R.string.geek_tickets_loading), style = Geek.type.body, color = colors.onGlassMuted)
            messages.isEmpty() -> Text(stringResource(R.string.geek_tickets_no_messages), style = Geek.type.body, color = colors.onGlassMuted)
            else -> messages.forEach { Bubble(it) }
        }
        if (!status.canReply) {
            Text(stringResource(R.string.geek_tickets_closed), style = Geek.type.caption, color = colors.onGlassMuted)
        } else if (messages != null) {
            MessageField(state.reply, actions::onReply, stringResource(R.string.geek_tickets_reply_label), minHeight = 72.dp)
            if (state.tooShort) TooShort()
            GeekPrimaryButton(
                text = stringResource(R.string.geek_tickets_send),
                icon = GeekIcons.Check,
                onClick = actions::onSendReply,
                enabled = !state.sending,
            )
        }
    }
}

@Composable
private fun Bubble(message: TicketMessage) {
    val colors = Geek.colors
    val locale = appLocale()
    val support = message.fromSupport == true
    val author = stringResource(if (support) R.string.geek_tickets_from_support else R.string.geek_tickets_from_me)
    // Support on the reading side's start, the customer's own on its end, as in Telegram.
    Box(Modifier.fillMaxWidth(), contentAlignment = if (support) Alignment.CenterStart else Alignment.CenterEnd) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (support) colors.soft else colors.action)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                message.bodyFa.orEmpty(),
                style = Geek.type.body.copy(textDirection = TextDirection.Content),
                color = if (support) colors.onGlass else colors.onAction,
            )
            Text(
                listOfNotNull(author, formatDateTime(message.createdAt, locale)).joinToString(" · "),
                style = Geek.type.micro,
                color = if (support) colors.onGlassMuted else colors.onAction.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun NewTicket(state: TicketsUiState, actions: TicketsActions) {
    val colors = Geek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.geek_tickets_topic), style = Geek.type.label, color = colors.onGlass)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TicketTopic.entries.forEach { topic -> TopicChip(topic, topic == state.topic) { actions.onTopic(topic) } }
        }
        Text(stringResource(R.string.geek_tickets_subject), style = Geek.type.label, color = colors.onGlass)
        MessageField(state.subject, actions::onSubject, stringResource(R.string.geek_tickets_subject), minHeight = 24.dp, singleLine = true)
        Text(stringResource(R.string.geek_tickets_message), style = Geek.type.label, color = colors.onGlass)
        MessageField(state.body, actions::onBody, stringResource(R.string.geek_tickets_message), minHeight = 120.dp)
        if (state.tooShort) TooShort()
        GeekPrimaryButton(
            text = stringResource(R.string.geek_tickets_submit),
            icon = GeekIcons.Check,
            onClick = actions::onSubmit,
            enabled = !state.sending,
        )
    }
}

@Composable
private fun TopicChip(topic: TicketTopic, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(Geek.shapes.pill)
            .background(if (selected) colors.action else colors.soft)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(topic.label), style = Geek.type.caption, color = if (selected) colors.onAction else colors.onGlass)
    }
}

@Composable
private fun MessageField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    minHeight: Dp,
    singleLine: Boolean = false,
) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            // The label doubles as the hint while the field is empty.
            if (value.isEmpty()) Text(label, style = Geek.type.body, color = colors.onGlassMuted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = Geek.type.body.copy(color = colors.onGlass, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(colors.action),
                modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun TooShort() {
    Text(
        stringResource(R.string.geek_tickets_too_short, Tickets.MIN_MESSAGE),
        style = Geek.type.caption.copy(fontSize = 12.sp),
        color = Geek.colors.danger,
    )
}
