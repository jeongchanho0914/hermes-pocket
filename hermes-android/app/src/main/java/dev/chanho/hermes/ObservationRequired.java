package dev.chanho.hermes;

import org.json.JSONObject;

/** A recoverable observation failure, not permission to replay an old device action. */
final class ObservationRequired extends IllegalStateException {
    private final String reason;
    private ObservationRequired(String reason,String message){super(message);this.reason=reason;}
    static void checkSnapshot(String requested,String current,long ageMs){
        if(current==null||current.isEmpty()||!current.equals(requested))
            throw new ObservationRequired("snapshot_replaced","화면 정보가 교체되었습니다. read_screen으로 다시 조회한 뒤 대상을 새로 선택하세요.");
        if(ageMs<0||ageMs>45000)
            throw new ObservationRequired("snapshot_expired","화면 정보가 만료되었습니다. read_screen으로 다시 조회한 뒤 대상을 새로 선택하세요.");
    }
    static ObservationRequired changed(){return new ObservationRequired("surface_changed","대상 앱·창 또는 화면 범위가 바뀌었습니다. read_screen으로 다시 확인하세요.");}
    JSONObject result(){
        // A compound operation may already have performed an earlier step. Do not
        // misrepresent the entire tool as having had no side effects.
        return J.obj("ok",false,"errorCode","OBSERVATION_REQUIRED","reason",reason,
            "error",getMessage(),"recoverable",true,"verified",false,
            "recovery",J.obj("nextTool","read_screen","retrySameArguments",false,
                "requiresNewTargetSelection",true,"requiresExistingApprovalPolicy",true,
                "instruction","Read a fresh allowed screen, reselect the intended target, and verify the result. Never reuse the old snapshot, element ID or coordinate. Do not infer that earlier steps of a compound tool were undone."));
    }
}
