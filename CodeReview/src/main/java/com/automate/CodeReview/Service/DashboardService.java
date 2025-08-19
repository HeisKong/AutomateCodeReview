package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Models.HistoryModel;
import com.automate.CodeReview.Models.TrendsModel;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DashboardService {

    public DashboardModel getOverview(UUID id){
        return null;
    }

    public HistoryModel getHistory(UUID id){
        return null;
    }

    public TrendsModel getTrends(UUID id){
        return null;
    }
}
