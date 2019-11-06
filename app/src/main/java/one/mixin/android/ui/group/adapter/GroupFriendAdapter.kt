package one.mixin.android.ui.group.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter
import kotlinx.android.synthetic.main.item_contact_header.view.*
import kotlinx.android.synthetic.main.item_group_friend.view.*
import one.mixin.android.R
import one.mixin.android.extension.inflate
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.toast
import one.mixin.android.vo.User
import one.mixin.android.vo.showVerifiedOrBot

class GroupFriendAdapter : RecyclerView.Adapter<GroupFriendAdapter.FriendViewHolder>(),
    StickyRecyclerHeadersAdapter<GroupFriendAdapter.HeaderViewHolder> {

    companion object {
        const val MAX_CHECKED = 50
        const val MAX_USER_COUNT = 256
    }

    private var data: List<User>? = null
    private var mShowHeader: Boolean = false
    private var mListener: GroupFriendListener? = null
    private val mCheckedMap: HashMap<String, Boolean> = HashMap()
    var alreadyUserIds: List<String>? = null
    var isAdd: Boolean = true
    var isCreate: Boolean = false
    var selectCount = 0

    fun setData(data: List<User>?, showHeader: Boolean) {
        this.data = data
        mShowHeader = showHeader
        data?.filterNot { mCheckedMap.containsKey(it.identityNumber) }
            ?.forEach { mCheckedMap[it.identityNumber] = false }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data?.size ?: 0

    override fun getHeaderId(position: Int): Long {
        if (!mShowHeader) {
            return -1
        }
        return data.notNullWithElse({
            val u = it[position]
            if (u.fullName != null) {
                if (u.fullName.isEmpty()) ' '.toLong() else u.fullName[0].toLong()
            } else {
                -1L
            }
        }, -1L)
    }

    override fun onBindHeaderViewHolder(holder: HeaderViewHolder, position: Int) {
        if (data == null || data!!.isEmpty()) {
            return
        }
        val user = data!![position]
        holder.bind(user)
    }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): HeaderViewHolder {
        val view = parent.inflate(R.layout.item_contact_header, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        if (data == null || data!!.isEmpty()) {
            return
        }
        val user = data!![position]
        holder.bind(user, mCheckedMap, alreadyUserIds, isAdd, View.OnClickListener { itemView ->
            if (isCreate && selectCount >= MAX_CHECKED && !itemView.cb.isChecked) {
                itemView.context.toast(R.string.error_create_full_group)
                return@OnClickListener
            } else if (isAdd && !itemView.cb.isChecked && selectCount + alreadyUserIds.notNullWithElse(
                    { it.size },
                    0
                ) >= MAX_USER_COUNT
            ) {
                itemView.context.toast(R.string.error_create_full_group_add)
                return@OnClickListener
            }

            itemView.cb.isChecked = !itemView.cb.isChecked
            mCheckedMap[user.identityNumber] = itemView.cb.isChecked
            if (isCreate || isAdd) {
                if (itemView.cb.isChecked) {
                    selectCount++
                } else {
                    selectCount--
                }
            }
            mListener?.onItemClick(user, itemView.cb.isChecked)
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder =
        FriendViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_group_friend,
                parent,
                false
            )
        )

    fun setGroupFriendListener(listener: GroupFriendListener) {
        mListener = listener
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(
            user: User,
            checkedMap: HashMap<String, Boolean>,
            alreadyUserIds: List<String>?,
            isAdd: Boolean,
            onClickListener: View.OnClickListener
        ) {
            itemView.name.text = user.fullName
            itemView.avatar.setInfo(user.fullName, user.avatarUrl, user.userId)
            if (isAdd) {
                alreadyUserIds?.let {
                    if (it.contains(user.userId)) {
                        itemView.cb.setButtonDrawable(R.drawable.ic_round_gray)
                        itemView.isEnabled = false
                        user.showVerifiedOrBot(itemView.verified_iv, itemView.bot_iv)
                        return
                    } else {
                        itemView.cb.setButtonDrawable(R.drawable.cb_add_member)
                        itemView.isEnabled = true
                    }
                }
            }
            if (checkedMap.containsKey(user.identityNumber)) {
                itemView.cb.isChecked = checkedMap[user.identityNumber]!!
            }
            user.showVerifiedOrBot(itemView.verified_iv, itemView.bot_iv)
            itemView.cb.isClickable = false
            itemView.setOnClickListener(onClickListener)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(user: User) {
            itemView.header.text = if (user.fullName != null && user.fullName.isNotEmpty())
                user.fullName[0].toString() else ""
        }
    }

    interface GroupFriendListener {
        fun onItemClick(user: User, checked: Boolean)
    }
}
