package me.lampu.lampcord.shared.state

import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObjectProtocol
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

private var registered = false

private var observers: List<NSObjectProtocol> = emptyList()

internal actual fun registerVisibilityListener(onVisible: () -> Unit, onHidden: () -> Unit) {
    if (registered) return
    registered = true

    val center = NSNotificationCenter.defaultCenter
    observers = listOfNotNull(
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) {
            onVisible()
        },
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, null) {
            onHidden()
        }
    )
}
