package de.kopis.glacier;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import com.amazonaws.services.glacier.model.GetJobOutputRequest;
import com.amazonaws.services.glacier.model.GetJobOutputResult;
import com.amazonaws.services.glacier.model.InitiateJobRequest;
import com.amazonaws.services.glacier.model.InitiateJobResult;
import com.amazonaws.services.glacier.model.JobParameters;
import com.amazonaws.services.glacier.model.ResourceNotFoundException;

public class VaultInventoryLister extends AbstractGlacierCommand {

    public VaultInventoryLister(final File credentials) throws IOException {
        super(credentials);
    }

    public void startInventoryListing(final URL endpointUrl, final String vaultName) {
        System.out.println("Starting inventory listing for vault " + vaultName + "...");
        client.setEndpoint(endpointUrl.toExternalForm());
        final InitiateJobRequest initJobRequest = new InitiateJobRequest().withVaultName(vaultName).withJobParameters(new JobParameters().withType("inventory-retrieval"));
        final InitiateJobResult initJobResult = client.initiateJob(initJobRequest);
        final String jobId = initJobResult.getJobId();
        System.out.println("Inventory Job created with ID=" + jobId);
    }

    public void retrieveInventoryListing(final URL endpointUrl, final String vaultName, final String jobId) {
        System.out.println("Retrieving inventory for job id " + jobId + "...");
        client.setEndpoint(endpointUrl.toExternalForm());
        try {
            final GetJobOutputRequest jobOutputRequest = new GetJobOutputRequest().withVaultName(vaultName).withJobId(jobId);
            final GetJobOutputResult jobOutputResult = client.getJobOutput(jobOutputRequest);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(jobOutputResult.getBody()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (final ResourceNotFoundException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        } catch (final IOException e) {
            System.err.println(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
}

