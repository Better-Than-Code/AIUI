for file in app/src/main/java/com/cellular/rpc/ui/chat/CellularChatTab.kt \
            app/src/main/java/com/cellular/rpc/ui/settings/PallySettingsBottomSheet.kt \
            app/src/main/java/com/cellular/rpc/ui/widget/WidgetsAndRpcTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/PacketInspectorTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/E2ETestRunnerTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/OutboxTab.kt; do

    sed -i '3 a import androidx.compose.foundation.BorderStroke\nimport androidx.compose.ui.graphics.*\nimport androidx.compose.ui.graphics.drawscope.*\nimport androidx.compose.animation.AnimatedVisibility\nimport com.cellular.rpc.engine.dynamic.*\nimport androidx.compose.foundation.Canvas\nimport androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.unit.sp\nimport androidx.compose.foundation.lazy.LazyRow' "$file"
done
