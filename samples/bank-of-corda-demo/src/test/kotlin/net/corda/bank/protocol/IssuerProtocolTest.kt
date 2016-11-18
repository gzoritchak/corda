package net.corda.bank.protocol

import com.google.common.util.concurrent.ListenableFuture
import net.corda.bank.api.BOC_ISSUER_PARTY
import net.corda.bank.api.BOC_KEY
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.crypto.Party
import net.corda.core.map
import net.corda.core.protocols.ProtocolStateMachine
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.initiateSingleShotProtocol
import net.corda.testing.ledger
import net.corda.testing.node.MockNetwork
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class IssuerProtocolTest {

    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var bankOfCordaNode: MockNetwork.MockNode
    lateinit var bankClientNode: MockNetwork.MockNode

    @Test
    fun `test issuer protocol`() {

        net = MockNetwork(false, true)

        ledger {
            notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            bankOfCordaNode = net.createPartyNode(notaryNode.info.address, BOC_ISSUER_PARTY.name, BOC_KEY)
            bankClientNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name, MEGA_CORP_KEY)

            bankClientNode.disableDBCloseOnStop()
            bankOfCordaNode.disableDBCloseOnStop()

            val (issueRequest, issuerResult) = runIssuerAndIssueRequester(1000000.DOLLARS, MEGA_CORP)
            assertEquals(issuerResult.get(), issueRequest.get().resultFuture.get())

            bankOfCordaNode.stop()
            bankClientNode.stop()

            bankOfCordaNode.manuallyCloseDB()
            bankClientNode.manuallyCloseDB()
        }
    }

    private fun runIssuerAndIssueRequester(amount: Amount<Currency>, issueTo: Party) : RunResult {

        val issuerFuture = bankOfCordaNode.initiateSingleShotProtocol(IssuerProtocol.IssuanceRequester::class) {
            otherParty -> IssuerProtocol.Issuer(issueTo)
        }.map { it.psm }

        val issueRequest = IssuerProtocol.IssuanceRequester(amount, BOC_ISSUER_PARTY.name)
        val issueRequestResultFuture = bankClientNode.smm.add(issueRequest).resultFuture

        return RunResult(issuerFuture, issueRequestResultFuture)
    }

    private data class RunResult(
            val issuer: ListenableFuture<ProtocolStateMachine<*>>,
            val issueRequestResult: ListenableFuture<IssuerProtocolResult>
    )
}