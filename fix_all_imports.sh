IMPORTS="
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.PacketLogEntity
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.service.CellularServiceProfile
import com.cellular.rpc.domain.service.ServiceProtocolMode
import com.cellular.rpc.engine.*
import com.cellular.rpc.engine.dynamic.*
import com.cellular.rpc.ui.handshake.AwaitingTransactionsList
import com.cellular.rpc.ui.handshake.HandshakeSessionCard
import com.cellular.rpc.ui.handshake.UserInterventionDialog
import com.cellular.rpc.update.AppUpdateManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
"

for file in app/src/main/java/com/cellular/rpc/ui/chat/CellularChatTab.kt \
            app/src/main/java/com/cellular/rpc/ui/settings/PallySettingsBottomSheet.kt \
            app/src/main/java/com/cellular/rpc/ui/widget/WidgetsAndRpcTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/PacketInspectorTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/E2ETestRunnerTab.kt \
            app/src/main/java/com/cellular/rpc/ui/diagnostics/OutboxTab.kt; do
    
    # Remove existing imports (very aggressive, but we will prepend the unified block)
    sed -i '/^import /d' "$file"
    
    # Prepend imports after package declaration
    awk -v imports="$IMPORTS" '/^package / { print; print imports; next }1' "$file" > tmp_file && mv tmp_file "$file"
done
