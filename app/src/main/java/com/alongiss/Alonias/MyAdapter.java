package com.alongiss.Alonias;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Adapter for displaying the rooms inside a RecyclerView.
 * It connects the rooms list from Java code to the visual list on the screen.
 */
public class MyAdapter extends RecyclerView.Adapter<MyAdapter.RoomViewHolder> {

    private ArrayList<String> rooms;

    public MyAdapter(ArrayList<String> rooms) {
        this.rooms = rooms;
    }

    /**
     * Creates the visual layout for one item in the RecyclerView.
     *
     * LayoutInflater converts item_room.xml into a real View object
     * that can be displayed on the screen.
     */
    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room, parent, false);

        return new RoomViewHolder(view);
    }

    /**
     * Puts the correct room text inside the item at the given position.
     *
     * RecyclerView reuses item views for performance, so every time an item
     * appears on screen, this method updates it with the right data.
     */
    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        holder.tvRoom.setText(rooms.get(position));
    }

    /**
     * Returns how many room items should be displayed.
     */
    @Override
    public int getItemCount() {
        return rooms.size();
    }

    /**
     * ViewHolder stores the views of one RecyclerView item.
     *
     * This avoids calling findViewById again and again while scrolling,
     * which makes the RecyclerView more efficient.
     */
    static class RoomViewHolder extends RecyclerView.ViewHolder {

        TextView tvRoom;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);

            tvRoom = itemView.findViewById(R.id.tvRoomName);
        }
    }
}