package com.cellular.rpc.engine

import com.cellular.rpc.data.local.MutationLogEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AdminApprovalState {
    private val _pendingApprovals = MutableSharedFlow<MutationLogEntity>(extraBufferCapacity = 10)
    val pendingApprovals: SharedFlow<MutationLogEntity> = _pendingApprovals.asSharedFlow()

    fun emitPendingApproval(log: MutationLogEntity) {
        _pendingApprovals.tryEmit(log)
    }
}
