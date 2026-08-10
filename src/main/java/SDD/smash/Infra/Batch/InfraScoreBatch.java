package SDD.smash.Infra.Batch;


import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Infra.Dto.InfraScoreDTO;
import SDD.smash.Infra.Dto.InfraScoreUpsertDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Collectors;

import static SDD.smash.Util.BatchTextUtil.isBlank;
import static SDD.smash.Util.BatchTextUtil.normalize;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class InfraScoreBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final SigunguRepository sigunguRepository;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;


    private Set<String> sigunguCodeCache = null;

    private boolean isKnownSigunguCode(String code) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = sigunguRepository.findAll()
                    .stream()
                    .map(Sigungu::getSigunguCode)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(code);
    }
    @Value("${infraScore.filePath}")
    private String filePath;

    @Bean
    public Job infraScoreJob(){
        return new JobBuilder("infraScoreJob", jobRepository)
                .start(infraScoreStep())
                .build();
    }

    @Bean
    public Step infraScoreStep() {

        return new StepBuilder("infraScoreStep", jobRepository)
                .<InfraScoreDTO, InfraScoreUpsertDTO> chunk(100, platformTransactionManager)
                .reader(infraScoreCsvReader())
                .processor(infraScoreCsvProcessor())
                .writer(infraScoreWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InfraScoreDTO> infraScoreCsvReader() {

        return new FlatFileItemReaderBuilder<InfraScoreDTO>()
                .name("infraScoreCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "score")
                .fieldSetMapper(fieldSet -> {
                    String sigunguCode = normalize(fieldSet.readString(0));
                    Integer score = Integer.parseInt(normalize(fieldSet.readString(1)));
                    return new InfraScoreDTO(sigunguCode, score);
                })
                .build();
    }

    @Bean
    public ItemProcessor<InfraScoreDTO, InfraScoreUpsertDTO> infraScoreCsvProcessor(){
        return dto -> {
            String sigunguCode = dto.getSigungu_code();
            if (isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)) return null;

            return InfraScoreUpsertDTO.builder()
                    .sigunguCode(sigunguCode)
                    .score(dto.getScore())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<InfraScoreUpsertDTO> infraScoreWriter() {
        String upsertSql = """
            INSERT INTO InfraScore (sigungu_code, score)
            VALUES (:sigunguCode, :score)
            ON DUPLICATE KEY UPDATE score = VALUES(score)
            """;

        return new JdbcBatchItemWriterBuilder<InfraScoreUpsertDTO>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

}
