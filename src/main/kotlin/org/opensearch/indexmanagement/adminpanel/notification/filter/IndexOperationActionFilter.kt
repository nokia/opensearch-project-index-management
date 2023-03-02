/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.indexmanagement.adminpanel.notification.filter

import org.opensearch.action.ActionListener
import org.opensearch.action.ActionRequest
import org.opensearch.action.ActionResponse
import org.opensearch.action.admin.indices.forcemerge.ForceMergeAction
import org.opensearch.action.admin.indices.open.OpenIndexAction
import org.opensearch.action.admin.indices.shrink.ResizeAction
import org.opensearch.action.admin.indices.shrink.ResizeRequest
import org.opensearch.action.support.ActionFilter
import org.opensearch.action.support.ActionFilterChain
import org.opensearch.client.Client
import org.opensearch.cluster.service.ClusterService
import org.opensearch.core.xcontent.NamedXContentRegistry
import org.opensearch.index.reindex.ReindexAction
import org.opensearch.indexmanagement.notification.NotificationService
import org.opensearch.script.ScriptService
import org.opensearch.tasks.Task

class IndexOperationActionFilter(
    val client: Client,
    val clusterService: ClusterService,
    val xContentRegistry: NamedXContentRegistry,
    val notificationService: NotificationService,
    val scriptService: ScriptService
) : ActionFilter {
    override fun order() = Integer.MIN_VALUE
    override fun <Request : ActionRequest, Response : ActionResponse> apply(
        task: Task,
        action: String,
        request: Request,
        listener: ActionListener<Response>,
        chain: ActionFilterChain<Request, Response>
    ) {
        var wrappedListener: ActionListener<Response> = listener
        when (action) {
            ReindexAction.NAME,
            OpenIndexAction.NAME,
            ResizeAction.NAME,
            ForceMergeAction.NAME -> {
                if (task.parentTaskId.isSet == false) {
                    wrappedListener = NotificationActionListener(
                        delegate = listener,
                        client = client,
                        action = action,
                        clusterService = clusterService,
                        xContentRegistry = xContentRegistry,
                        notificationService = notificationService,
                        taskId = task.id,
                        scriptService = scriptService
                    )

                    if (request is ResizeRequest) {
                        wrappedListener.resizeType = request.resizeType
                    }
                }
            }
        }
        chain.proceed(task, action, request, wrappedListener)
    }
}
