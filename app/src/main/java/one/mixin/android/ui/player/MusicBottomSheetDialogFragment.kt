package one.mixin.android.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.databinding.FragmentMusicBottomSheetBinding
import one.mixin.android.db.MessageDao
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.booleanFromAttribute
import one.mixin.android.extension.checkInlinePermissions
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.withArgs
import one.mixin.android.job.AttachmentDownloadJob
import one.mixin.android.job.MixinJobManager
import one.mixin.android.ui.player.internal.MusicServiceConnection
import one.mixin.android.util.AudioPlayer
import one.mixin.android.util.SystemUIManager
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.MediaStatus
import one.mixin.android.webrtc.EXTRA_CONVERSATION_ID
import one.mixin.android.widget.MixinBottomSheetDialog
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MusicBottomSheetDialogFragment : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "MusicBottomSheetDialogFragment"

        fun newInstance(conversationId: String) = MusicBottomSheetDialogFragment().withArgs {
            putString(EXTRA_CONVERSATION_ID, conversationId)
        }
    }

    private val stopScope = scope(Lifecycle.Event.ON_STOP)

    private val binding by viewBinding(FragmentMusicBottomSheetBinding::inflate)

    private val conversationId: String by lazy {
        requireArguments().getString(EXTRA_CONVERSATION_ID)!!
    }

    @Inject
    lateinit var musicServiceConnection: MusicServiceConnection

    private val viewModel by viewModels<MusicViewModel> {
        provideMusicViewModel(musicServiceConnection, conversationId)
    }

    @Inject
    lateinit var jobManager: MixinJobManager

    @Inject
    lateinit var messageDao: MessageDao

    override fun getTheme() = R.style.MixinBottomSheet

    private val listAdapter = MediaItemAdapter()

    private lateinit var contentView: View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MixinBottomSheetDialog(requireContext(), theme).apply {
            dismissWithAnimation = true
        }
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = binding.root
        dialog.setContentView(contentView)

        val params = (contentView.parent as View).layoutParams as? CoordinatorLayout.LayoutParams
        val behavior = params?.behavior as? BottomSheetBehavior<*>
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(contentView.width, View.MeasureSpec.EXACTLY),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        behavior?.skipCollapsed = true
        behavior?.peekHeight = contentView.measuredHeight

        binding.apply {
            listAdapter.listener = object : MediaItemListener {
                override fun onItemClick(mediaItem: MediaItemData) {
                    viewModel.playMedia(mediaItem) {}
                }

                override fun onDownload(mediaItem: MediaItemData) {
                    download(mediaItem)
                }

                override fun onCancel(mediaItem: MediaItemData) {
                    cancel(mediaItem)
                }
            }
            titleView.rightAnimator.setOnClickListener { dismiss() }
            titleView.leftIb.setOnClickListener {
                alertDialogBuilder()
                    .setMessage(getString(R.string.player_delete_all_desc))
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.player_delete_all) { _, _ ->
                        (requireActivity() as MusicActivity).serviceStopped = true
                        viewModel.stopMusicService()
                        dismiss()
                    }
                    .show()
            }
            playlistRv.layoutManager = LinearLayoutManager(requireContext())
            playlistRv.adapter = listAdapter

            musicLayout.progress.isVisible = false

            viewModel.subscribe()
            viewModel.mediaItems.observe(
                this@MusicBottomSheetDialogFragment,
                { list ->
                    listAdapter.submitList(list)

                    var mediaItem = list.find { it.mediaId == AudioPlayer.get().exoPlayer.currentMediaItem?.mediaId }
                    if (mediaItem == null) {
                        mediaItem = list.firstOrNull()
                    }
                    musicLayout.title.text = mediaItem?.title
                    musicLayout.subtitle.text = mediaItem?.subtitle
                    musicLayout.albumArt.loadImage(mediaItem?.albumArtUri)
                }
            )
            playerControlView.player = AudioPlayer.get().exoPlayer
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            SystemUIManager.lightUI(
                window,
                !requireContext().booleanFromAttribute(R.attr.flag_night)
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        requireActivity().finish()
    }

    override fun dismiss() {
        if (checkFloatingPermission()) {
            dismissAllowingStateLoss()
        }
    }

    override fun dismissAllowingStateLoss() {
        try {
            super.dismissAllowingStateLoss()
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
    }

    private var permissionAlert: AlertDialog? = null

    private fun checkFloatingPermission() =
        requireContext().checkInlinePermissions {
            if (permissionAlert != null && permissionAlert!!.isShowing) return@checkInlinePermissions

            permissionAlert = AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(R.string.web_floating_permission)
                .setPositiveButton(R.string.live_setting) { dialog, _ ->
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${requireContext().packageName}")
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                    dialog.dismiss()
                }.show()
        }

    private fun download(mediaItemData: MediaItemData) {
        RxPermissions(requireActivity())
            .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .autoDispose(stopScope)
            .subscribe(
                { granted ->
                    if (granted) {
                        lifecycleScope.launch {
                            messageDao.suspendFindMessageById(mediaItemData.mediaId)?.let {
                                jobManager.addJobInBackground(AttachmentDownloadJob(it))
                            }
                        }
                    } else {
                        context?.openPermissionSetting()
                    }
                },
                {}
            )
    }

    private fun cancel(mediaItemData: MediaItemData) = lifecycleScope.launch(Dispatchers.IO) {
        jobManager.cancelJobByMixinJobId(mediaItemData.mediaId) {
            lifecycleScope.launch {
                messageDao.updateMediaStatusSuspend(MediaStatus.CANCELED.name, mediaItemData.mediaId)
            }
        }
    }
}
