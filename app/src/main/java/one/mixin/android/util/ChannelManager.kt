package one.mixin.android.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.notificationManager
import one.mixin.android.extension.putBoolean
import one.mixin.android.extension.putInt
import one.mixin.android.extension.supportsOreo
import one.mixin.android.extension.supportsQ
import one.mixin.android.job.BlazeMessageService
import timber.log.Timber

class ChannelManager {

    companion object {
        private const val CHANNEL_GROUP = "channel_group"
        const val CHANNEL_MESSAGE = "channel_message"
        private const val CHANNEL_SILENCE_MESSAGE = "channel_silence_message"
        private const val CHANNEL_CURRENT_VERSION = "channel_current_version"
        private const val CHANNEL_CURRENT_USER_VERSION = "channel_current_user_version"
        private const val CHANNEL_VERSION = 2

        private const val CHANNEL_MESSAGE_GROUP = "channel_message_group"

        const val MESSAGES = 0
        const val GROUP = 1
        const val SILENCE = 2

        @IntDef(GROUP, MESSAGES, SILENCE)
        @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
        annotation class ChannelCategory

        @RequiresApi(Build.VERSION_CODES.O)
        fun createNodeChannel(notificationManager: NotificationManager) {
            val channel = NotificationChannel(
                BlazeMessageService.CHANNEL_NODE,
                MixinApplication.get().getString(R.string.notification_node),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            channel.setSound(null, null)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun initChannelGroup(context: Context) {
            val notificationManager = context.notificationManager
            val notificationChannelGroups = notificationManager.notificationChannelGroups
            if (notificationChannelGroups.isEmpty() || !notificationChannelGroups.any { channelGroup -> channelGroup.id == CHANNEL_MESSAGE_GROUP })
                notificationManager.createNotificationChannelGroup(
                    NotificationChannelGroup(
                        CHANNEL_MESSAGE_GROUP,
                        context.getString(R.string.notification_channel_group)
                    )
                )
        }

        fun create(context: Context) {
            supportsOreo {
                initChannelGroup(context)
                val messageChannel =
                    NotificationChannel(
                        getChannelId(MESSAGES),
                        context.getString(R.string.notification_message),
                        NotificationManager.IMPORTANCE_HIGH
                    )

                val uri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/mixin")
                messageChannel.group = CHANNEL_MESSAGE_GROUP
                messageChannel.setSound(
                    uri,
                    AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                messageChannel.enableVibration(true)
                messageChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                val groupChannel = copyChannel(messageChannel, getChannelId(GROUP))
                val silenceChannel = copyChannel(messageChannel, getChannelId(SILENCE))
                groupChannel.name = context.getString(R.string.notification_group)
                silenceChannel.name = context.getString(R.string.notification_silence)
                silenceChannel.setSound(null, null)
                silenceChannel.enableVibration(false)

                supportsQ {
                    groupChannel.setAllowBubbles(true)
                    messageChannel.setAllowBubbles(true)
                    silenceChannel.setAllowBubbles(true)
                }
                context.notificationManager.createNotificationChannels(
                    listOf(messageChannel, groupChannel, silenceChannel)
                )
            }
        }

        @Synchronized
        fun updateChannelSound(context: Context) {
            supportsOreo {
                val channelUpdatedWithVersion = "$CHANNEL_CURRENT_VERSION$CHANNEL_VERSION"
                // first check current version channel is already updated
                if (context.defaultSharedPreferences.getBoolean(channelUpdatedWithVersion, false)) {
                    return
                }

                // then delete all old CHANNEL_GROUP and CHANNEL_MESSAGE channels
                deleteChannels(context)

                // finally create new channel and update SP
                create(context)
                context.defaultSharedPreferences.putBoolean(channelUpdatedWithVersion, true)
            }
        }

        fun resetChannelSound(context: Context) {
            supportsOreo {
                val currentUserVersion = context.defaultSharedPreferences.getInt(CHANNEL_CURRENT_USER_VERSION, 0)
                deleteChannels(context)
                context.defaultSharedPreferences.putInt(CHANNEL_CURRENT_USER_VERSION, currentUserVersion + 1)
                create(context)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun deleteChannels(context: Context) {
            val existingChannels =
                context.notificationManager.notificationChannels ?: return
            try {
                context.notificationManager.notificationChannelGroups.forEach {
                    context.notificationManager.deleteNotificationChannelGroup(it.id)
                }
                existingChannels.forEach {
                    if (it.id.startsWith(CHANNEL_GROUP) || it.id.startsWith(CHANNEL_MESSAGE) || it.id.startsWith(CHANNEL_SILENCE_MESSAGE)) {
                        context.notificationManager.deleteNotificationChannel(it.id)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun getChannelId(@ChannelCategory category: Int): String {
            val currentUserVersion = MixinApplication.appContext.defaultSharedPreferences.getInt(CHANNEL_CURRENT_USER_VERSION, 0)
            return when (category) {
                GROUP -> {
                    "${CHANNEL_GROUP}_$CHANNEL_VERSION.$currentUserVersion"
                }
                SILENCE -> {
                    "${CHANNEL_SILENCE_MESSAGE}_$CHANNEL_VERSION.$currentUserVersion"
                }
                else -> {
                    "${CHANNEL_MESSAGE}_$CHANNEL_VERSION.$currentUserVersion"
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun copyChannel(original: NotificationChannel, id: String): NotificationChannel {
            val copy = NotificationChannel(id, original.name, original.importance)

            copy.group = original.group
            copy.setSound(original.sound, original.audioAttributes)
            copy.setBypassDnd(original.canBypassDnd())
            copy.enableVibration(original.shouldVibrate())
            copy.vibrationPattern = original.vibrationPattern
            copy.lockscreenVisibility = original.lockscreenVisibility
            copy.setShowBadge(original.canShowBadge())
            copy.lightColor = original.lightColor
            copy.enableLights(original.shouldShowLights())

            return copy
        }
    }
}
