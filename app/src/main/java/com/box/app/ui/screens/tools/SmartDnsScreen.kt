package com.box.app.ui.screens.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.home.homeSuccessColors
import com.box.app.ui.components.home.homeNeutralColors
import com.box.app.ui.components.home.homeDangerColors
import com.box.app.ui.components.home.homeWarningColors
import com.box.app.ui.components.home.homeInfoColors
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SDNS_BASE = "/data/adb/smartdns"
private const val SDNS_CONF = "$SDNS_BASE/smartdns/smartdns.conf"
private const val SDNS_SETTING = "$SDNS_BASE/setting.conf"
private const val SDNS_SERVICE = "$SDNS_BASE/scripts/dns.service"
private const val SDNS_TOOL = "$SDNS_BASE/scripts/dns.tool"
private const val SDNS_LOG = "$SDNS_BASE/run/smartdns.log"

/**
 * SmartDNS WebUI 增强自动登录 JS。
 *
 * 多策略并行：
 * 1. API 直接登录（fetch POST /api/login）— 最快最可靠
 * 2. DOM 表单填写 + 提交 — 兜底方案
 * 3. MutationObserver 持续监听 — 处理 SPA 延迟渲染
 * 4. 轮询重试（最多 10 次，间隔 500ms）— 应对时序问题
 */
fun buildSmartDnsAutoLoginJs(user: String, passwd: String): String {
    val u = user.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    val p = passwd.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    return """
(function(){
  if(window.__sdnsLoginDone) return;

  var USER='$u', PASS='$p', MAX=10, tries=0;

  function markDone(){ window.__sdnsLoginDone=true; }

  // ── 策略 1：API 直接登录 ──
  function tryApiLogin(){
    var base=location.origin;
    // SmartDNS WebUI 常见 API 端点
    var endpoints=['/api/login','/login','/api/auth'];
    endpoints.forEach(function(ep){
      fetch(base+ep,{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({username:USER,password:PASS,user:USER,passwd:PASS})
      }).then(function(r){
        if(r.ok){ markDone(); setTimeout(function(){ location.reload(); },500); }
      }).catch(function(){});
      // 也尝试 form-urlencoded
      fetch(base+ep,{
        method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded'},
        body:'username='+encodeURIComponent(USER)+'&password='+encodeURIComponent(PASS)
      }).then(function(r){
        if(r.ok){ markDone(); setTimeout(function(){ location.reload(); },500); }
      }).catch(function(){});
    });
  }

  // ── 策略 2：DOM 表单填写 ──
  function fillForm(){
    var pw=document.querySelector('input[type="password"]');
    if(!pw) return false;
    var un=document.querySelector('input[name="username"],input[name="user"],input[type="text"],input[name="account"]');
    if(!un) return false;

    try{
      var setter=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;
      // 填写用户名
      setter.call(un,USER);
      un.dispatchEvent(new Event('input',{bubbles:true}));
      un.dispatchEvent(new Event('change',{bubbles:true}));
      un.dispatchEvent(new Event('blur',{bubbles:true}));
      // 填写密码
      setter.call(pw,PASS);
      pw.dispatchEvent(new Event('input',{bubbles:true}));
      pw.dispatchEvent(new Event('change',{bubbles:true}));
      pw.dispatchEvent(new Event('blur',{bubbles:true}));
    }catch(e){
      // 简单赋值兜底
      un.value=USER; pw.value=PASS;
    }

    // 延迟后点击提交
    setTimeout(function(){
      // 按优先级查找提交按钮
      var btn=document.querySelector(
        'button[type="submit"],input[type="submit"],' +
        '.btn-login,.login-btn,.btn-primary,' +
        'button.el-button--primary,button.ant-btn-primary'
      );
      if(!btn){
        var form=un.closest('form');
        if(form){
          btn=form.querySelector('button');
          if(!btn){ form.submit(); markDone(); return; }
        }
      }
      if(!btn){
        // 最后兜底：页面上所有 button 中找包含"登录/login/sign"文字的
        var all=document.querySelectorAll('button');
        for(var i=0;i<all.length;i++){
          var t=(all[i].textContent||'').toLowerCase();
          if(t.indexOf('login')>=0||t.indexOf('登录')>=0||t.indexOf('sign')>=0||t.indexOf('submit')>=0){
            btn=all[i]; break;
          }
        }
      }
      if(btn){ btn.click(); markDone(); }
    },300);
    return true;
  }

  // ── 策略 3：轮询重试 ──
  function poll(){
    if(window.__sdnsLoginDone) return;
    if(tries>=MAX) return;
    tries++;
    // 如果没有密码框说明已登录
    if(!document.querySelector('input[type="password"]')) { markDone(); return; }
    if(!fillForm()){ setTimeout(poll,500); }
  }

  // ── 策略 4：MutationObserver 监听 DOM 变化 ──
  function observe(){
    if(typeof MutationObserver==='undefined') return;
    var obs=new MutationObserver(function(){
      if(window.__sdnsLoginDone){ obs.disconnect(); return; }
      var pw=document.querySelector('input[type="password"]');
      if(pw){
        obs.disconnect();
        setTimeout(function(){ if(!window.__sdnsLoginDone) fillForm(); },200);
      }
    });
    obs.observe(document.body||document.documentElement,{childList:true,subtree:true});
    // 安全超时：10 秒后断开
    setTimeout(function(){ obs.disconnect(); },10000);
  }

  // ── 启动所有策略 ──
  tryApiLogin();
  if(document.readyState==='complete'||document.readyState==='interactive'){
    setTimeout(poll,300);
  } else {
    window.addEventListener('DOMContentLoaded',function(){ setTimeout(poll,300); });
  }
  observe();
})();
""".trimIndent()
}

@Composable
fun SmartDnsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenConfigEditor: (String) -> Unit,
    onOpenWebUi: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    // ── 状态 ──
    var isRunning by remember { mutableStateOf(false) }
    var pid by remember { mutableStateOf("-") }
    var isBusy by remember { mutableStateOf(false) }
    var autoRun by remember { mutableStateOf(true) }
    var oomProtect by remember { mutableStateOf(true) }
    var ruleAutoUpdate by remember { mutableStateOf(true) }
    var ruleUpdateTime by remember { mutableStateOf("12:00") }
    var webUiUrl by remember { mutableStateOf("http://127.0.0.1:6080") }
    var webUiUser by remember { mutableStateOf("root") }
    var webUiPasswd by remember { mutableStateOf("root") }
    var logContent by remember { mutableStateOf("") }
    var moduleInstalled by remember { mutableStateOf(false) }

    fun parseSetting(text: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    fun parseConfValue(text: String, key: String): String? {
        val regex = Regex("""^\s*${Regex.escape(key)}\s+(.+)$""", setOf(RegexOption.MULTILINE))
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    suspend fun refreshStatus() {
        withContext(Dispatchers.IO) {
            val checkRes = ShellExecutor.execute("[ -f $SDNS_CONF ] && echo ok")
            moduleInstalled = checkRes.stdout.trim() == "ok"
            if (!moduleInstalled) return@withContext

            val pidRes = ShellExecutor.execute("pidof smartdns 2>/dev/null")
            val rawPid = pidRes.stdout.trim()
            pid = rawPid.ifBlank { "-" }
            isRunning = rawPid.isNotBlank()

            val settingRes = ShellExecutor.execute("cat $SDNS_SETTING 2>/dev/null")
            val st = settingRes.stdout
            autoRun = parseSetting(st, "AUTO_RUN")?.toBooleanStrictOrNull() ?: true
            oomProtect = parseSetting(st, "OOM_PROTECT")?.toBooleanStrictOrNull() ?: true
            ruleAutoUpdate = parseSetting(st, "RULE_AUTO_UPDATE")?.toBooleanStrictOrNull() ?: true
            ruleUpdateTime = parseSetting(st, "RULE_UPDATE_TIME") ?: "12:00"

            val confRes = ShellExecutor.execute("cat $SDNS_CONF 2>/dev/null")
            val ct = confRes.stdout
            parseConfValue(ct, "smartdns-ui.ip")?.let { webUiUrl = it }
            parseConfValue(ct, "smartdns-ui.user")?.let { webUiUser = it }
            parseConfValue(ct, "smartdns-ui.password")?.let { webUiPasswd = it }

            val logRes = ShellExecutor.execute("tail -n 30 $SDNS_LOG 2>/dev/null")
            logContent = logRes.stdout.trim()
        }
    }

    fun writeSettingBool(key: String, value: Boolean) {
        scope.launch(Dispatchers.IO) {
            val v = if (value) "true" else "false"
            ShellExecutor.execute("sed -i 's/^${key}=.*/${key}=${v}/' $SDNS_SETTING")
        }
    }

    fun controlService(action: String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            withContext(Dispatchers.IO) { ShellExecutor.execute("sh $SDNS_SERVICE $action") }
            delay(1000)
            refreshStatus()
            isBusy = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
        while (isActive) { delay(5000); refreshStatus() }
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false)
                else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    val scheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "SmartDNS",
                subtitle = if (isRunning) stringResource(R.string.smartdns_status_running)
                    else stringResource(R.string.smartdns_status_stopped),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = scheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refreshStatus() } }) {
                        Icon(Icons.Filled.Refresh, null, tint = scheme.onSurface)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp, end = 12.dp,
                top = innerPadding.calculateTopPadding(), extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!moduleInstalled) {
                item(key = "not_installed") {
                    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.smartdns_not_installed), style = MiuixTheme.textStyles.body1, color = scheme.onSurfaceSecondary)
                        }
                    }
                }
                return@LazyColumn
            }

            // ═══ 服务状态（HeroCard 风格） ═══
            item(key = "c_status") {
                val statusColors = if (isRunning) homeSuccessColors()
                    else if (isBusy) homeWarningColors()
                    else homeNeutralColors()
                val animatedAccent by animateColorAsState(
                    statusColors.accent, animationSpec = tween(360), label = "sdns_accent"
                )
                val statusText = when {
                    isRunning -> stringResource(R.string.smartdns_status_running)
                    isBusy -> stringResource(R.string.smartdns_action_restart)
                    else -> stringResource(R.string.smartdns_status_stopped)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = scheme.surfaceContainer)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── 上半区：左侧状态 | 右侧信息 ──
                        val cardH = 88.dp
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 左卡：图标 + 状态
                            Card(
                                modifier = Modifier.weight(1f).height(cardH),
                                cornerRadius = 16.dp,
                                insideMargin = PaddingValues(12.dp),
                                colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest)
                            ) {
                                Row(
                                    Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Dns,
                                        contentDescription = null,
                                        tint = animatedAccent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = "SmartDNS",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = scheme.onSurfaceSecondary
                                        )
                                        Text(
                                            text = statusText,
                                            style = MiuixTheme.textStyles.title3,
                                            fontWeight = FontWeight.Bold,
                                            color = animatedAccent
                                        )
                                    }
                                }
                            }

                            // 右卡：PID / 端口 / 自启
                            Card(
                                modifier = Modifier.weight(1f).height(cardH),
                                cornerRadius = 16.dp,
                                insideMargin = PaddingValues(12.dp),
                                colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest)
                            ) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    SdnsHeroInfoRow("PID", if (isRunning) pid else "-")
                                    SdnsHeroInfoRow("WebUI", webUiUrl.removePrefix("http://"))
                                    SdnsHeroInfoRow(
                                        stringResource(R.string.smartdns_auto_run),
                                        if (autoRun) stringResource(R.string.smartdns_status_running) else stringResource(R.string.smartdns_status_stopped)
                                    )
                                }
                            }
                        }

                        // ── 操作按钮（AnimatedContent 过渡） ──
                        AnimatedContent(
                            targetState = isRunning,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 })
                                    .togetherWith(fadeOut(tween(160)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "sdns_btns"
                        ) { running ->
                            if (running) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SdnsActionButton(
                                        text = stringResource(R.string.smartdns_action_stop),
                                        tone = SdnsBtnTone.Danger,
                                        enabled = !isBusy,
                                        onClick = { controlService("stop") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SdnsActionButton(
                                        text = stringResource(R.string.smartdns_action_restart),
                                        tone = SdnsBtnTone.Warning,
                                        enabled = !isBusy,
                                        onClick = { controlService("restart") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                SdnsActionButton(
                                    text = stringResource(R.string.smartdns_action_start),
                                    tone = SdnsBtnTone.Primary,
                                    enabled = !isBusy,
                                    onClick = { controlService("start") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // ═══ WebUI ═══
            item(key = "s_webui") { SmallTitle(text = "WebUI") }
            item(key = "c_webui") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = stringResource(R.string.smartdns_webui_open), summary = webUiUrl, onClick = onOpenWebUi)
                        SdnsDivider()
                        SdnsInfoRow(stringResource(R.string.smartdns_webui_user), webUiUser)
                        SdnsDivider()
                        SdnsInfoRow(stringResource(R.string.smartdns_webui_password), webUiPasswd)
                    }
                }
            }

            // ═══ 配置管理 ═══
            item(key = "s_config") { SmallTitle(text = stringResource(R.string.smartdns_section_config)) }
            item(key = "c_config") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = stringResource(R.string.smartdns_config_dns), summary = "smartdns.conf", onClick = { onOpenConfigEditor(SDNS_CONF) })
                        SdnsDivider()
                        ArrowPreference(title = stringResource(R.string.smartdns_config_module), summary = "setting.conf", onClick = { onOpenConfigEditor(SDNS_SETTING) })
                    }
                }
            }

            // ═══ 模块设置 ═══
            item(key = "s_settings") { SmallTitle(text = stringResource(R.string.smartdns_section_settings)) }
            item(key = "c_settings") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        SwitchPreference(checked = autoRun, onCheckedChange = { autoRun = it; writeSettingBool("AUTO_RUN", it) },
                            title = stringResource(R.string.smartdns_auto_run), summary = stringResource(R.string.smartdns_auto_run_subtitle))
                        SdnsDivider()
                        SwitchPreference(checked = oomProtect, onCheckedChange = { oomProtect = it; writeSettingBool("OOM_PROTECT", it) },
                            title = stringResource(R.string.smartdns_oom_protect), summary = stringResource(R.string.smartdns_oom_protect_subtitle))
                        SdnsDivider()
                        SwitchPreference(checked = ruleAutoUpdate, onCheckedChange = { ruleAutoUpdate = it; writeSettingBool("RULE_AUTO_UPDATE", it) },
                            title = stringResource(R.string.smartdns_rule_auto_update), summary = stringResource(R.string.smartdns_rule_auto_update_subtitle, ruleUpdateTime))
                        SdnsDivider()
                        ArrowPreference(title = stringResource(R.string.smartdns_update_rules_now), summary = stringResource(R.string.smartdns_update_rules_now_subtitle),
                            onClick = { scope.launch(Dispatchers.IO) { ShellExecutor.execute("sh $SDNS_TOOL update") } })
                    }
                }
            }

            // ═══ 日志 ═══
            item(key = "s_logs") { SmallTitle(text = stringResource(R.string.smartdns_section_logs)) }
            item(key = "c_logs") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        if (logContent.isBlank()) {
                            Text(stringResource(R.string.smartdns_no_logs), style = MiuixTheme.textStyles.body2, color = scheme.onSurfaceSecondary)
                        } else {
                            Text(logContent, style = MiuixTheme.textStyles.footnote2, color = scheme.onSurfaceSecondary, maxLines = 15, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SdnsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f)))
}

@Composable
private fun SdnsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}

// ── HeroCard 风格信息行 ────────────────────────────────────────────────────

@Composable
private fun SdnsHeroInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Tonal 风格操作按钮（与 HeroActionButton 一致） ─────────────────────────

private enum class SdnsBtnTone { Primary, Danger, Warning }

@Composable
private fun SdnsActionButton(
    text: String,
    tone: SdnsBtnTone = SdnsBtnTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = when (tone) {
        SdnsBtnTone.Primary -> homeInfoColors()
        SdnsBtnTone.Danger -> homeDangerColors()
        SdnsBtnTone.Warning -> homeWarningColors()
    }
    val (bg, fg) = colors.container to colors.onContainer
    val animBg by animateColorAsState(bg, tween(320), label = "sdns_btn_bg")
    val animFg by animateColorAsState(fg, tween(320), label = "sdns_btn_fg")

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        cornerRadius = 14.dp,
        colors = ButtonDefaults.buttonColors(
            color = animBg,
            disabledColor = animBg.copy(alpha = 0.68f),
            contentColor = animFg,
            disabledContentColor = animFg.copy(alpha = 0.65f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                InfiniteProgressIndicator(modifier = Modifier.size(14.dp))
                Spacer(Modifier.padding(end = 8.dp))
            }
            Text(text, style = MiuixTheme.textStyles.button, color = animFg)
        }
    }
}
