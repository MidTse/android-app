package one.mixin.android.ui.setting

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.databinding.FragmentPinLogsBinding
import one.mixin.android.databinding.ItemPinLogsBinding
import one.mixin.android.extension.localTime
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.LogResponse

@AndroidEntryPoint
class PinLogsFragment : BaseFragment(R.layout.fragment_pin_logs) {
    private val viewModel by viewModels<SettingViewModel>()
    private val binding by viewBinding(FragmentPinLogsBinding::bind)

    companion object {
        const val TAG = "PinLogsFragment"
        fun newInstance() = PinLogsFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            title.leftIb.setOnClickListener { activity?.onBackPressed() }
            list.adapter = adapter
            list.setOnScrollChangeListener { _, _, _, _, _ ->
                if (isAdded) {
                    if (!list.canScrollVertically(1)) {
                        loadMore()
                    }
                }
            }
            lifecycleScope.launch {
                isLoading = true
                handleMixinResponse(
                    invokeNetwork = {
                        viewModel.getPinLogs()
                    },
                    successBlock = { result ->
                        hasMore = result.data?.isNotEmpty() == true
                        empty.isVisible = false
                        list.isVisible = true
                        adapter.data.addAll(result.data!!)
                        adapter.notifyDataSetChanged()
                        isLoading = false
                        progress.isVisible = false
                    },
                    defaultExceptionHandle = {
                        hasMore = false
                        empty.isVisible = true
                        list.isVisible = false
                        isLoading = false
                        progress.isVisible = false
                        ErrorHandler.handleError(it)
                    }
                )
            }
        }
    }

    private var hasMore = false
    private var isLoading = false
    private fun loadMore() {
        if (isLoading || !hasMore) {
            return
        }
        isLoading = true
        lifecycleScope.launch {
            handleMixinResponse(
                invokeNetwork = {
                    viewModel.getPinLogs(adapter.data.last().createdAt)
                },
                successBlock = { result ->
                    adapter.data.addAll(result.data!!)
                    adapter.notifyDataSetChanged()
                    isLoading = false
                },
                defaultExceptionHandle = {
                    hasMore = false
                    isLoading = false
                    ErrorHandler.handleError(it)
                }
            )
        }
    }

    private val adapter by lazy {
        PinAdapter()
    }

    class PinHolder(private val itemBinding: ItemPinLogsBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(pin: LogResponse) {
            val result = getLogDescription(itemView.context, pin.code)
            itemBinding.apply {
                logTitle.text = result.first
                logDesc.text = result.second
                logCreated.text = pin.createdAt.localTime()
                logAddress.text = pin.ipAddress
            }
        }

        private fun getLogDescription(context: Context, code: String): Pair<String, String> {
            when (code) {
                "VERIFICATION" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_verification))
                "RAW_TRANSFER" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_raw_transfer))
                "USER_TRANSFER" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_user_transfer))
                "WITHDRAWAL" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_withdrawal))
                "ADD_ADDRESS" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_add_address))
                "DELETE_ADDRESS" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_delete_address))
                "ADD_EMERGENCY" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_add_emergency))
                "DELETE_EMERGENCY" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_delete_emergency))
                "READ_EMERGENCY" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_read_emergency))
                "UPDATE_PHONE" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_update_phone))
                "UPDATE_PIN" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_update_pin))
                "MULTISIG_SIGN" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_multisig_sign))
                "MULTISIG_UNLOCK" ->
                    return Pair(context.getString(R.string.log_category_pin_incorrect), context.getString(R.string.log_pin_multisig_unlock))
                "ACTIVITY_PIN_MODIFICATION" ->
                    return Pair(context.getString(R.string.log_category_pin_change), context.getString(R.string.log_pin_modification))
                "ACTIVITY_EMERGENCY_CONTACT_MODIFICATION" ->
                    return Pair(context.getString(R.string.log_category_emergency), context.getString(R.string.log_emergency_modification))
                "ACTIVITY_PHONE_MODIFICATION" ->
                    return Pair(context.getString(R.string.log_category_phone_change), context.getString(R.string.log_phone_modification))
                "ACTIVITY_LOGIN_BY_PHONE" ->
                    return Pair(context.getString(R.string.log_category_login), context.getString(R.string.log_login_phone))
                "ACTIVITY_LOGIN_BY_EMERGENCY_CONTACT" ->
                    return Pair(context.getString(R.string.log_category_login), context.getString(R.string.log_login_emergency))
                "ACTIVITY_LOGIN_FROM_DESKTOP" ->
                    return Pair(context.getString(R.string.log_category_login), context.getString(R.string.log_login_desktop))
                else ->
                    return Pair(code, code)
            }
        }
    }

    class PinAdapter : RecyclerView.Adapter<PinHolder>() {
        var data: MutableList<LogResponse> = mutableListOf()
        override fun getItemCount(): Int = data.size

        fun getItem(position: Int) = data.get(position)
        override fun onBindViewHolder(holder: PinHolder, position: Int) {
            getItem(position).let {
                holder.bind(it)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinHolder = PinHolder(
            ItemPinLogsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
}
