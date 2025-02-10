package com.star4droid.star2d.Activities;

import android.view.LayoutInflater;
import android.widget.ImageView;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import android.view.View;
import android.os.Bundle;
import java.util.ArrayList;
import android.widget.LinearLayout;
import java.util.List;
import java.util.HashMap;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.app.AppCompatActivity;
import com.star4droid.star2d.evo.R;
public class ProjectsActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout buttonContainer;
    private List<HashMap<String, Object>> dataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        viewPager = findViewById(R.id.viewPager);
        buttonContainer = findViewById(R.id.buttonContainer);

        // Populate sample data
        for(int i = 0; i < 5; i++) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("imageRes", R.drawable.background);
            dataList.add(map);
        }

        viewPager.setAdapter(new CardAdapter());
        viewPager.setOffscreenPageLimit(3);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                handleButtonAnimation(position);
            }
        });

        // Page transformation for scaling effect
        viewPager.setPageTransformer(new ScaleInTransformer());
    }

    private void handleButtonAnimation(int position) {
        if (buttonContainer.getVisibility() == View.INVISIBLE) {
            buttonContainer.setVisibility(View.VISIBLE);
            buttonContainer.animate()
                    .scaleX(1).scaleY(1)
                    .setDuration(300)
                    .start();
        }
    }

    private class ScaleInTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            CardView cardView = page.findViewById(R.id.cardView);
            float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
            
            cardView.animate()
                    .scaleX(scaleFactor)
                    .scaleY(scaleFactor)
                    .setDuration(200)
                    .start();
        }
    }

    private class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HashMap<String, Object> item = dataList.get(position);
            holder.imageView.setBackgroundResource((Integer) item.get("imageRes"));
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
            }
        }
    }
}