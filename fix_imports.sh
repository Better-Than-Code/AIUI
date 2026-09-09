for file in app/src/main/java/com/cellular/rpc/ui/chat/CellularChatTab.kt \
            app/src/main/java/com/cellular/rpc/ui/settings/PallySettingsBottomSheet.kt \
            app/src/main/java/com/cellular/rpc/ui/widget/WidgetsAndRpcTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/PacketInspectorTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/E2ETestRunnerTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/OutboxTab.kt; do
    sed -i 's/^import /import com.example.ui.theme.*\nimport androidx.compose.material.icons.filled.*\nimport com.cellular.rpc.engine.WidgetData\nimport kotlinx.coroutines.launch\nimport androidx.compose.ui.text.font.FontWeight\nimport /' "$file"
done
