package SDD.smash.Infra.Batch;


import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Infra.Dto.InfraDTO;
import SDD.smash.Infra.Dto.InfraUpsertDTO;
import SDD.smash.Infra.Entity.Industry;
import SDD.smash.Infra.Repository.IndustryRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.stream.Collectors;

import static SDD.smash.Util.BatchTextUtil.*;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class InfraBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final IndustryRepository industryRepository;
    private final SigunguRepository sigunguRepository;
    private final @Qualifier("dataDBSource") DataSource dataDataSource;


    private Set<String> sigunguCodeCache = null;
    private Set<String> industryCodeCache = null;

    private boolean isKnownSigunguCode(String sigunguCode) {
        if (sigunguCodeCache == null) {
            sigunguCodeCache = sigunguRepository.findAll()
                    .stream()
                    .map(Sigungu::getSigunguCode)
                    .collect(Collectors.toSet());
        }
        return sigunguCodeCache.contains(sigunguCode);
    }

    private boolean isKnownIndustryCode(String industryCode) {
        if (industryCodeCache == null) {
            industryCodeCache = industryRepository.findAll()
                    .stream()
                    .map(Industry::getCode)
                    .collect(Collectors.toSet());
        }
        return industryCodeCache.contains(industryCode);
    }

    @Value("${infra.filePath}")
    private String filePath;

    @Bean
    public Job infraJob(){
        return new JobBuilder("infraJob", jobRepository)
                .start(infraStep())
                .build();
    }

    @Bean
    public Step infraStep() {

        return new StepBuilder("infraStep", jobRepository)
                .<InfraDTO, InfraUpsertDTO> chunk(500, platformTransactionManager)
                .reader(infraCsvReader())
                .processor(infraCsvProcessor())
                .writer(infraWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InfraDTO> infraCsvReader() {

        return new FlatFileItemReaderBuilder<InfraDTO>()
                .name("infraCsvReader")
                .resource(new FileSystemResource(filePath))
                .encoding("MS949")
                .linesToSkip(1)
                .strict(true)
                .delimited()
                .delimiter(",")
                .quoteCharacter('\0')
                .names("sigungu_code", "industry_code","count","ratio","score")
                .fieldSetMapper(fieldSet -> {
                    String rawSigunguCode = normalize(fieldSet.readString(0));
                    String rawIndustryCode = normalize(fieldSet.readString(1));
                    String rawInfraName = normalize(fieldSet.readString(2));
                    BigDecimal rawRatio = new BigDecimal(normalize(fieldSet.readString(3)))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal rawScore = new BigDecimal(normalize(fieldSet.readString(4)))
                            .setScale(2, RoundingMode.HALF_UP);

                    return new InfraDTO(rawSigunguCode, rawIndustryCode, rawInfraName,rawRatio,rawScore);
                })
                .build();
    }

    @Bean
    public ItemProcessor<InfraDTO, InfraUpsertDTO> infraCsvProcessor(){
        return dto -> {
            String sigunguCode = dto.getSigungu_code();
            String industryCode = dto.getIndustry_code();
            if(isBlank(sigunguCode) || !isKnownSigunguCode(sigunguCode)){
                return null;
            } else if(isBlank(industryCode) || !isKnownIndustryCode(industryCode)){
                return null;
            }
            return InfraUpsertDTO.builder()
                    .sigunguCode(sigunguCode)
                    .industryCode(industryCode)
                    .count(dto.getCount())
                    .ratio(dto.getRatio())
                    .score(dto.getScore())
                    .build();
        };
    }

    @Bean
    public JdbcBatchItemWriter<InfraUpsertDTO> infraWriter() {
        String upsertSql = """
            INSERT INTO infra (sigungu_code, industry_code, count, ratio, score)
            VALUES (:sigunguCode, :industryCode, :count, :ratio, :score)
            ON DUPLICATE KEY UPDATE count = VALUES(count)
            """;

        return new JdbcBatchItemWriterBuilder<InfraUpsertDTO>()
                .dataSource(dataDataSource)
                .sql(upsertSql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

}
