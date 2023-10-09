package faang.school.accountservice.mapper;

import faang.school.accountservice.dto.BalanceAuditDto;
import faang.school.accountservice.model.Balance;
import faang.school.accountservice.model.BalanceAudit;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BalanceAuditMapper {

    BalanceAudit toBalanceAudit(Balance balance);

    BalanceAuditDto toBalance(BalanceAudit balanceAudit);
}