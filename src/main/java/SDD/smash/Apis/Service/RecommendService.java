package SDD.smash.Apis.Service;

import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Apis.Dto.CodeNameDTO;
import SDD.smash.Apis.Dto.RecommendDTO;
import SDD.smash.Apis.Dto.ScoreDTO;
import SDD.smash.Dwelling.Entity.DwellingType;
import SDD.smash.Dwelling.Service.DwellingScoreSerivce;
import SDD.smash.Dwelling.Service.DwellingService;
<<<<<<< HEAD
=======
import SDD.smash.Infra.Entity.InfraImportance;
>>>>>>> origin/Backup/main
import SDD.smash.Infra.Service.InfraScoreService;
import SDD.smash.Infra.Service.InfraService;
import SDD.smash.Job.Service.JobScoreService;
import SDD.smash.Job.Service.JobService;
<<<<<<< HEAD
import SDD.smash.Support.service.SupportScoreService;
import SDD.smash.Support.service.SupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
=======
import SDD.smash.Support.domain.SupportTag;
import SDD.smash.Support.service.SupportScoreService;
import SDD.smash.Support.service.SupportService;
import lombok.RequiredArgsConstructor;
>>>>>>> origin/Backup/main
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

<<<<<<< HEAD
@Slf4j
=======
>>>>>>> origin/Backup/main
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final JobScoreService jobScoreService;
    private final DwellingScoreSerivce dwellingScoreService;
    private final SupportScoreService supportScoreService;
    private final InfraScoreService infraScoreService;

    private final JobService jobService;
    private final DwellingService dwellingService;
    private final SupportService supportService;
    private final InfraService infraService;

    private final SigunguRepository sigunguRepository;

    @Transactional(readOnly = true)
    public List<RecommendDTO> recommend(
<<<<<<< HEAD
            Integer supportChoice, //필수
            String midJobCode,
            DwellingType dwellingType, //필수
            Integer price, //필수
            Integer infraChoice //필수
=======
            SupportTag supportTag,
            String midJobCode,
            DwellingType dwellingType, //필수
            Integer price, //필수
            InfraImportance infraImportance //필수
>>>>>>> origin/Backup/main
    )
    {
        Map<String, Integer> jobScoreMap = jobScoreService.getJobScore(midJobCode);
        Map<String, Integer> dwellingScoreMap = dwellingScoreService.getDwellingScoreByType(dwellingType, price);
<<<<<<< HEAD
        Map<String, Integer> supportScoreMap = supportScoreService.getSupportScoresByTag(supportChoice);
        Map<String, Integer> infraScoreMap = infraScoreService.getInfraScoresByChoice(infraChoice);
=======
        Map<String, Integer> supportScoreMap = supportScoreService.getSupportScoresByTag(supportTag);
        Map<String, Integer> infraScoreMap = infraScoreService.getInfraScoresByImportance(infraImportance);
>>>>>>> origin/Backup/main

        List<CodeNameDTO> codeNames = sigunguRepository.findAllCodeNames();

        List<ScoreDTO> scores = new ArrayList<>();

<<<<<<< HEAD
        int div = 4;
        if(supportChoice == null || supportChoice == 0) div--; //정책 선택 안 한 경우 점수 산정 제외
        if(infraChoice == null || infraChoice == 0) div--; //인프라 선택 안 한 경우 점수 산정 제외


        for(CodeNameDTO dto : codeNames)
        {
            String sidoCode = dto.getSidoCode();
            // 서울-경기-인천 제외
            if(sidoCode.equals("41") || sidoCode.equals("11") || sidoCode.equals("28")){
                continue;
            }
=======
        int div = (supportTag == null) ? 3 : 4;
        for(CodeNameDTO dto : codeNames)
        {
>>>>>>> origin/Backup/main
            String code = dto.getSigunguCode();
            Integer jobScore = jobScoreMap.getOrDefault(code, 0);
            Integer dwellingScore = dwellingScoreMap.getOrDefault(code, 0);
            Integer supportScore = supportScoreMap.getOrDefault(code, 0);
            Integer infraScore = infraScoreMap.getOrDefault(code, 0);

            int sum = jobScore + dwellingScore + supportScore + infraScore;

            scores.add(new ScoreDTO(dto.getSidoCode(), dto.getSidoName(), dto.getSigunguCode(), dto.getSigunguName(),
                    (Integer)(sum / div)));
        }

        scores.sort((a,b) -> b.getScore().compareTo(a.getScore()));

        List<ScoreDTO> top10 = scores.size() > 10 ? scores.subList(0, 10) : scores;
<<<<<<< HEAD
        List<RecommendDTO> result = new ArrayList<>();
        Integer maxScore = top10.get(0).getScore();

        for(ScoreDTO dto : top10)
        {
            String sigunguCode = dto.getSigunguCode();
            Integer finalScore = (int) Math.round(((double) dto.getScore() / maxScore) * 100);
=======

        List<RecommendDTO> result = new ArrayList<>();
        for(ScoreDTO dto : top10)
        {
            String sigunguCode = dto.getSigunguCode();
            Integer score = dto.getScore();
>>>>>>> origin/Backup/main

            RecommendDTO rd = RecommendDTO.builder()
                    .sidoCode(dto.getSidoCode())
                    .sidoName(dto.getSidoName())
                    .sigunguCode(dto.getSigunguCode())
                    .sigunguName(dto.getSigunguName())
<<<<<<< HEAD
                    .score(finalScore)
=======
                    .score(score)
>>>>>>> origin/Backup/main

                    .totalJobInfo(jobService.getJobInfoBySigungu(sigunguCode))
                    .fitJobInfo(jobService.getJobInfoBySigunguAndJobCode(sigunguCode, midJobCode)) //jobCode 없는 경우 null

                    .totalSupportNum(supportService.getAllSupportNum(sigunguCode))
<<<<<<< HEAD
                    .fitSupportNum(supportService.getFitSupportNum(sigunguCode, supportChoice)) //tag없는 경우 null

                    .dwellingSimpleInfo(dwellingService.getDwellingSimpleInfo(sigunguCode))

                    .infraMajors(infraService.getMajorInfraNumAndScore(sigunguCode))
=======
                    .fitSupportNum(supportService.getFitSupportNum(sigunguCode, supportTag)) //tag없는 경우 null

                    .dwellingSimpleInfo(dwellingService.getDwellingSimpleInfo(sigunguCode))

                    .infraMajors(infraService.getMajorInfraNum(sigunguCode))
>>>>>>> origin/Backup/main
                    .build();
            result.add(rd);
        }
        return result;
    }


}
