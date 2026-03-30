package com.ember.reader.ui.reader.common

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import timber.log.Timber

@Composable
fun NavigatorContainer(
    key: Any,
    containerId: Int,
    fragmentTag: String,
    fragmentClass: Class<out Fragment>,
    fragmentFactory: FragmentFactory,
    locatorFlow: (Fragment) -> StateFlow<Locator>?,
    onLocatorChanged: (Locator) -> Unit,
    onNavigatorReady: (Fragment) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    var containerView by remember { mutableStateOf<FragmentContainerView?>(null) }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                containerView = this
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(key, containerView) {
        if (containerView == null) return@DisposableEffect onDispose {}

        val scope = CoroutineScope(Dispatchers.Main + Job())

        // Use post to ensure the view is attached to the window before committing
        containerView?.post {
            val existing = fragmentManager.findFragmentByTag(fragmentTag)
            if (existing != null) {
                onNavigatorReady(existing)
            } else {
                fragmentManager.fragmentFactory = fragmentFactory
                fragmentManager.commitNow {
                    add(containerId, fragmentClass, null, fragmentTag)
                }
                fragmentManager.findFragmentByTag(fragmentTag)?.let { fragment ->
                    onNavigatorReady(fragment)
                }
            }

            // Start collecting locator updates after fragment is committed
            scope.launch {
                // Wait for navigator to initialize its internal state
                delay(500)
                val fragment = fragmentManager.findFragmentByTag(fragmentTag)
                if (fragment == null) {
                    Timber.w("NavigatorContainer: fragment not found after commit")
                    return@launch
                }
                locatorFlow(fragment)?.collect { locator ->
                    onLocatorChanged(locator)
                }
            }
        }

        onDispose {
            scope.cancel()
            fragmentManager.findFragmentByTag(fragmentTag)?.let { fragment ->
                fragmentManager.commitNow { remove(fragment) }
            }
        }
    }
}

private fun CoroutineScope.cancel() {
    val job = coroutineContext[Job]
    job?.cancel()
}
