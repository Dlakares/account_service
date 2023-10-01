package faang.school.accountservice.service;

import faang.school.accountservice.entity.AccountNumbersSequence;
import faang.school.accountservice.entity.FreeAccountNumber;
import faang.school.accountservice.entity.account.AccountType;
import faang.school.accountservice.exception.NoFreeAccountNumber;
import faang.school.accountservice.exception.TypeNotFoundException;
import faang.school.accountservice.repository.AccountNumbersSequenceRepository;
import faang.school.accountservice.repository.FreeAccountNumberRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class FreeAccountNumberService {
    private final FreeAccountNumberRepository freeAccountNumberRepository;
    private final AccountNumbersSequenceRepository accountNumberSequenceRepository;
    private final static int MINIMAL_FREE_ACCOUNT_NUM = 5;

    public BigInteger getFreeNumber(AccountType accountType, Consumer<String> action) {
        Optional<FreeAccountNumber> freeAccountNumber = freeAccountNumberRepository.getFreeAccountNumber(accountType.getValue());
        if (freeAccountNumber.isEmpty()){
            log.error("No free accounts left");
            throw new NoFreeAccountNumber(accountType);
        }
        BigInteger count = freeAccountNumberRepository.getFreeAccountNumberCountByType(accountType.getValue());
        if (count.compareTo(BigInteger.valueOf(MINIMAL_FREE_ACCOUNT_NUM)) < 0) {
            generateNewAccountNumbers(2, accountType)
                    .exceptionally(ex -> {
                        log.error("An error occurred while generating new account numbers: " + ex.getMessage());
                        throw new RuntimeException(ex);
                    })
                    .thenRun(() -> log.info("Less than {} accounts left. New accounts generated", MINIMAL_FREE_ACCOUNT_NUM));
        }

        String accountNumber = freeAccountNumber.get().getAccountNumber().toString();

        action.accept(MessageFormat.format("Generated number {0}",accountNumber));
        return new BigInteger(String.valueOf(accountNumber));
    }

    @Transactional
    @Retryable(
            retryFor = PersistenceException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 3000))
    private synchronized void generateFreeAccNumber(AccountType accountType){
        AccountNumbersSequence accountNumbersSequence =
                accountNumberSequenceRepository.findByAccountType(accountType.getValue())
                        .orElseThrow(() -> new TypeNotFoundException(accountType));

        BigInteger currentCount = accountNumbersSequence.getCurrentNumber().add(BigInteger.ONE);
        accountNumbersSequence.setCurrentNumber(currentCount);

        accountNumberSequenceRepository.save(accountNumbersSequence);

        String accountNumber = String.format("%s%020d", accountType.getValue(), currentCount);

        FreeAccountNumber freeAccountNumber = FreeAccountNumber.builder()
                .id(new FreeAccountNumber.FreeAccountNumberKey(accountType.getValue(),new BigInteger(accountNumber)))
                .accountType(accountType.getValue())
                .accountNumber(new BigInteger(accountNumber))
                .build();

        freeAccountNumberRepository.save(freeAccountNumber);

        log.info("{} is saved to db", freeAccountNumber.getAccountNumber());
    }

    @PostConstruct
    private void init() {

        generateNewAccountNumbers(2, AccountType.BUSINESS_CHECKING);
        generateNewAccountNumbers(2, AccountType.FOREIGN_CURRENCY);

        log.info("Initial free account numbers generated");
    }

    private CompletableFuture<Void> generateNewAccountNumbers(int number, AccountType accountType) {
        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < number; i++) {
                generateFreeAccNumber(accountType);
            }
        });
    }
}
