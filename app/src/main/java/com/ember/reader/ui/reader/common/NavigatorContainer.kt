package com.ember.reader.ui.reader.common

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

@Composable
fun <F : Fragment> NavigatorContainer(
    key: Any,
    containerId: Int,
    fragmentTag: String,
    fragmentClass: Class<F>,
    fragmentFactory: FragmentFactory,
    locatorFlow: (F) -> StateFlow<Locator>?,
    onLocatorChanged: (Locator) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(key) {
        val existingFragment = fragmentManager.findFragmentByTag(fragmentTag)
        if (existingFragment == null) {
            activity.supportFragmentManager.fragmentFactory = fragmentFactory
            fragmentManager.commit {
                add(containerId, fragmentClass, null, fragmentTag)
            }
        }

        val scope = CoroutineScope(Dispatchers.Main + Job())
        val locatorJob = scope.launch {
            @Suppress("UNCHECKED_CAST")
            val fragment = fragmentManager.findFragmentByTag(fragmentTag) as? F
            fragment?.let { locatorFlow(it) }?.collect { locator ->
                onLocatorChanged(locator)
            }
        }

        onDispose {
            locatorJob.cancel()
            fragmentManager.findFragmentByTag(fragmentTag)?.let { fragment ->
                fragmentManager.commit { remove(fragment) }
            }
        }
    }
}
