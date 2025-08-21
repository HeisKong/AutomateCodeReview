package com.automate.CodeReview.Service;


import com.automate.CodeReview.Models.ScanLogModel;
import com.automate.CodeReview.Models.ScanModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ScanService {

    public ScanModel startScan(UUID repositoryId){
        return null;
    }

    public List<ScanModel> getAllScan(){
        return null;
    }

    public ScanModel GetByIdScan(UUID id){
        //จะใช้อันนนี้ก้ได้นะเเต่ถ้าทำใน repo มันใช้ jdbc ดึงได้เลย
        return null;
    }

    public ScanModel getLogScan(UUID id){
        return null;
    }

    public ScanModel cancelScan(UUID id){
        return null;
    }

    public ScanLogModel getScanLogById(UUID id) {
        return null;
    }
}
