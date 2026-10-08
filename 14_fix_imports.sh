#!/data/data/com.termux/files/usr/bin/bash
set -e

DST="$HOME/DuoPlay"
UI="$DST/app/src/main/java/com/duoplay/video/ui"
ROOT="$DST/app/src/main/java/com/duoplay/DuoPlayApp.kt"

echo "▸ Fix 1: Correct ClassReelRoot import in DuoPlayApp.kt"
sed -i 's|import com.duoplay.video.ClassReelRoot|import com.duoplay.video.ui.ClassReelRoot|' "$ROOT"
grep -n "ClassReelRoot" "$ROOT"

echo ""
echo "▸ Fix 2: Rename ThumbCache in HistoryScreen.kt → HistoryThumbCache"
sed -i 's/\bThumbCache\b/HistoryThumbCache/g' "$UI/HistoryScreen.kt"
grep -n "HistoryThumbCache" "$UI/HistoryScreen.kt" | head -5

echo ""
echo "▸ Fix 3: Rename ThumbCache in HomeScreen.kt → HomeThumbCache"
sed -i 's/\bThumbCache\b/HomeThumbCache/g' "$UI/HomeScreen.kt"
grep -n "HomeThumbCache" "$UI/HomeScreen.kt" | head -5

echo ""
echo "✅ Script 14 done: imports + ThumbCache names fixed"
