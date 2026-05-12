package org.cagnulein.qzcompanionnordictracktreadmill.ui;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceRegistry;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceRegistry.DeviceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnDeviceSelectedListener {
        void onDeviceSelected(DeviceId id);
    }

    // ── Flat item list (headers + devices) ────────────────────────────────────

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_DEVICE = 1;

    private static class Item {
        final int type;
        final String label;
        final DeviceId deviceId;

        private Item(int type, String label, DeviceId deviceId) {
            this.type     = type;
            this.label    = label;
            this.deviceId = deviceId;
        }

        static Item header(String label)  { return new Item(TYPE_HEADER, label, null); }
        static Item device(DeviceId id)   { return new Item(TYPE_DEVICE, null, id); }
    }

    private static final List<Item> ITEMS;
    static {
        List<Item> items = new ArrayList<>();
        items.add(Item.header("BIKES"));
        for (DeviceId id : DeviceId.values())
            if (id.category == DeviceRegistry.Category.BIKE) items.add(Item.device(id));
        items.add(Item.header("TREADMILLS"));
        for (DeviceId id : DeviceId.values())
            if (id.category == DeviceRegistry.Category.TREADMILL) items.add(Item.device(id));
        items.add(Item.header("OTHER"));
        for (DeviceId id : DeviceId.values())
            if (id.category == DeviceRegistry.Category.OTHER) items.add(Item.device(id));
        ITEMS = Collections.unmodifiableList(items);
    }

    // ── Adapter state ──────────────────────────────────────────────────────────

    private int selectedPosition = RecyclerView.NO_POSITION;
    private final OnDeviceSelectedListener listener;

    DeviceAdapter(OnDeviceSelectedListener listener) {
        this.listener = listener;
    }

    void setSelectedId(DeviceId id) {
        for (int i = 0; i < ITEMS.size(); i++) {
            if (ITEMS.get(i).type == TYPE_DEVICE && ITEMS.get(i).deviceId == id) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                return;
            }
        }
    }

    DeviceId getSelectedId() {
        if (selectedPosition == RecyclerView.NO_POSITION) return DeviceId.other;
        Item item = ITEMS.get(selectedPosition);
        return item.type == TYPE_DEVICE ? item.deviceId : DeviceId.other;
    }

    // ── RecyclerView adapter methods ───────────────────────────────────────────

    @Override public int getItemViewType(int position) { return ITEMS.get(position).type; }
    @Override public int getItemCount() { return ITEMS.size(); }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setAllCaps(true);
            tv.setTextColor(0xFF80CBC4); // teal_200 — matches colorSecondary
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            int px12 = dpToPx(parent, 12);
            int px16 = dpToPx(parent, 16);
            int px4  = dpToPx(parent, 4);
            tv.setPadding(px16, px12, px16, px4);
            tv.setClickable(false);
            return new HeaderViewHolder(tv);
        } else {
            RadioButton rb = new RadioButton(parent.getContext());
            rb.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            rb.setClickable(false);
            rb.setFocusable(false);
            return new DeviceViewHolder(rb);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Item item = ITEMS.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).label.setText(item.label);
        } else {
            DeviceViewHolder dvh = (DeviceViewHolder) holder;
            dvh.radioButton.setText(DeviceRegistry.forId(item.deviceId).displayName());
            dvh.radioButton.setChecked(position == selectedPosition);
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                int prev = selectedPosition;
                selectedPosition = pos;
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                listener.onDeviceSelected(ITEMS.get(pos).deviceId);
            });
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView label;
        HeaderViewHolder(TextView tv) { super(tv); label = tv; }
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        final RadioButton radioButton;
        DeviceViewHolder(RadioButton rb) { super(rb); radioButton = rb; }
    }

    private static int dpToPx(ViewGroup parent, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                parent.getContext().getResources().getDisplayMetrics()));
    }
}
