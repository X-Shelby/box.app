#!/system/bin/sh

# ── 加载配置（路径变量 + 用户配置均在 setting.conf 中定义）──
. /data/adb/smartdns/setting.conf

LOG="$RUN_PATH/tool.log"
TMP="$RUN_PATH/rule_tmp"

_bb_found=0
for _bb in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do
    if [ -x "$_bb" ] && "$_bb" --list 2>/dev/null | grep -qx 'crond'; then
        BUSYBOX_BIN="$_bb"
        _bb_found=1
        break
    fi
done

unset _bb _bb_found

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"
    echo "$*"
}

update_rule() {
    local name="$1"
    local url="$2"
    local dest="$RULES_PATH/$name"
    local tmp_file="$TMP/$name"

    log "正在更新规则: $name"
    log "  下载地址: $url"

    mkdir -p "$TMP"

    curl -sfL --connect-timeout 15 --retry 2 -o "$tmp_file" "$url"
    if [ $? -ne 0 ] || [ ! -s "$tmp_file" ]; then
        log "  ✗ 下载失败，跳过: $name"
        rm -f "$tmp_file"
        return 1
    fi

    local new_lines old_lines
    new_lines=$(wc -l < "$tmp_file")
    old_lines=0
    [ -f "$dest" ] && old_lines=$(wc -l < "$dest")

    mv "$tmp_file" "$dest"
    log "  ✓ 更新完成: $name (旧: ${old_lines}行 → 新: ${new_lines}行)"
    return 0
}

do_update() {
    : > "$LOG"

    log "==============================="
    log "开始更新规则"
    log "==============================="

    local updated=0
    local failed=0
    local i=1

    while true; do
        eval "name=\${RULE${i}_NAME}"
        eval "url=\${RULE${i}_URL}"

        [ -z "$name" ] || [ -z "$url" ] && break

        if update_rule "$name" "$url"; then
            updated=$((updated + 1))
        else
            failed=$((failed + 1))
        fi

        i=$((i + 1))
    done

    rm -rf "$TMP"

    log "-------------------------------"
    log "更新完成 (成功: $updated / 失败: $failed)"

    if [ "$updated" -gt 0 ]; then
        "$SCRIPTS_PATH/dns.service" restart > /dev/null 2>&1
    else
        log "无规则更新成功，跳过重启"
    fi

    log "==============================="
}

start_cron() {
    $BUSYBOX_BIN pkill -f "crond -c $RUN_PATH" 2>/dev/null

    local raw="${RULE_UPDATE_TIME:-03:00}"
    local hour min
    hour=$(echo "$raw" | cut -d: -f1 | awk '{print int($0)}')
    min=$(echo  "$raw" | cut -d: -f2 | awk '{print int($0)}')

    mkdir -p "$RUN_PATH"
    {
        echo "SHELL=/system/bin/sh"
        echo "# smartdns rule auto-update"
        echo "$min $hour * * * $SCRIPTS_PATH/dns.tool update"
    } > "$RUN_PATH/root"
    chmod 0644 "$RUN_PATH/root"

    nohup $BUSYBOX_BIN crond -c "$RUN_PATH" > /dev/null 2>&1 &
    log "crond 已启动，更新时间: $raw"
}

stop_cron() {
    $BUSYBOX_BIN pkill -f "crond -c $RUN_PATH" 2>/dev/null
    log "crond 已停止"
}

case "$1" in
    update)
        do_update
        ;;
    cron)
        start_cron
        ;;
    kcron)
        stop_cron
        ;;
    *)
        echo "使用: $0 update(立即更新) | cron(启动定时任务) | kcron(停止定时任务)"
        exit 1
        ;;
esac
