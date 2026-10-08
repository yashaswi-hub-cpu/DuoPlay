#!/data/data/com.termux/files/usr/bin/bash
set -e

DST="$HOME/DuoPlay"
UI="$DST/app/src/main/java/com/duoplay/video/ui"

echo "▸ Reverting PlayerScreen's engine param back to non-null..."
sed -i 's/engine: CaptionEngine?/engine: CaptionEngine/' "$UI/PlayerScreen.kt"

echo "▸ Verifying the change..."
grep -n "engine:" "$UI/PlayerScreen.kt" | head -5

echo "▸ Making sure ClassReelRoot passes a real CaptionEngine instance..."
grep -n "engine = engine" "$UI/ClassReelRoot.kt" || true

echo ""
echo "✅ Script 12 done"
echo "   (ClassReelRoot already passes a real CaptionEngine — no change needed there)"
