package com.example;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
 
// As a heads up, many of these were explored but not used.
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.TransformInner;
import com.azure.resourcemanager.mediaservices.models.BuiltInStandardEncoderPreset;
import com.azure.resourcemanager.mediaservices.models.EncoderNamedPreset;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutput;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpoint;
import com.azure.resourcemanager.mediaservices.models.TransformOutput;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import io.github.cdimascio.dotenv.Dotenv;

public class App 
{
    public static void main( String[] args )
    {
        // I'm using VS Code credentials.
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        Dotenv dotenv = Dotenv.configure()
        .directory("./example1")
        .ignoreIfMalformed()
        .ignoreIfMissing()
        .load();

        AzureProfile profile = new AzureProfile(
            dotenv.get("AZURE_TENANT_ID"), 
            dotenv.get("AZURE_SUBSCRIPTION_ID"),
            AzureEnvironment.AZURE);
        
        MediaServicesManager manager = MediaServicesManager.authenticate(credential, profile);
  
        // Config info, and random variables.  Update the .env file (copy the sample.env to .env file first)
        String resourceGroupName = dotenv.get("RESOURCEGROUP");
        String accountName =  dotenv.get("ACCOUNTNAME");

        // SAMPLE CONFIGURATION SETTINGS
        // ----------------------------------------------------
        String uniqueString = UUID.randomUUID().toString().split("-")[0];
        String assetName = "JavaTester1" + uniqueString;
        String fileName = "SampleVideo_1280x720_10mb.mp4"; // This is actually supposed to be a path, but I just placed file in project root
        String blobName = "movie.mp4"; // This is the name of the blob we transfer upload our video into
        String transformName = "CustomTransformJava";
        String jobName = "TestJavaJob" + uniqueString;
        String outputName = "TestJavaOutput" + uniqueString;
        
        // I just sort of enabled everything on the SAS token in order to get it to work. 
        String sasUrl = dotenv.get ("REMOTESTORAGEACCOUNTSAS");
        String storageURL = "https://" + dotenv.get("STORAGEACCOUNTNAME") + ".blob.core.windows.net";
        //-----------------------------------------------------

        // Getting list of streaming endpoints, then printing them as an example of Iterables
        PagedIterable<StreamingEndpoint> endpoints = manager.streamingEndpoints().list(resourceGroupName, accountName);
        
        for(StreamingEndpoint endpoint : endpoints){
            System.out.printf("/nStreaming Endpoint found: %s", endpoint.name() );
        }
        
        // Now we're going to work on posting a job, and all that entails
        // We start by creating an Azure Media Services client using the serviceClient function from manager.
        AzureMediaServices ams = manager.serviceClient();
        
        // We create a blank slate of an Asset
        AssetInner inner = new AssetInner();
        
        // We then create (or update) an asset. It returns an Asset, which is stored in the AssetInner "test." 
        AssetInner test = ams.getAssets().createOrUpdate(resourceGroupName, accountName, assetName, inner); // This doesn't change "inner." 
        System.out.printf("/nCreated Asset : %s/n", test.name());
        
        // Next, we need to change gears and head over to storage SDK in order to upload our video.
        // Let's try to authenticate. 
        BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
        .endpoint(storageURL)
        .sasToken(sasUrl)
            .containerName(test.container())
            .buildClient();
            
        System.out.println(blobContainerClient.getAccountName());
        
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        
        // This code works, but only works to upload it once, then causes errors. Can fix with a try
        // blobClient.uploadFromFile(fileName);
        
        // Create preset
        EncoderNamedPreset namedPreset = EncoderNamedPreset.CONTENT_AWARE_ENCODING;
        BuiltInStandardEncoderPreset preset = new BuiltInStandardEncoderPreset().withPresetName(namedPreset);
        
        // Create Output
        TransformOutput outputOne = new TransformOutput().withPreset(preset);
        TransformOutput[] outputArray = {outputOne};
        
        // Create Transform            
        List<TransformOutput> outputs = Arrays.asList(outputArray);

        TransformInner transformInner = new TransformInner()
            .withOutputs(outputs);

        TransformInner newTransform = ams.getTransforms().createOrUpdate(resourceGroupName, accountName, transformName, transformInner);

        // Need to create an input asset, an output asset
        JobInputAsset chosenAsset = new JobInputAsset().withAssetName(test.name());
        JobOutputAsset outputAsset = new JobOutputAsset().withAssetName(assetName + "_OUTPUT");

        // You'll need to have an asset ready to "catch" the results. 
        ams.getAssets().createOrUpdate(resourceGroupName, accountName, assetName + "_OUTPUT", inner);

        // JobOutputAsset extends JobOutput, and JobInputAsset extends JobInput
        JobOutput[] outputArr = {outputAsset};
        List<JobOutput> jobOutputList = Arrays.asList(outputArr);
        
        // We need to define the job with inputs and outputs
        JobInner jobInner = new JobInner().withInput(chosenAsset).withOutputs(jobOutputList);

        // and from here we can go ahead and create that job.  Check the portal for when it's done.  Consider using
        // createWithResponse and exploring the methods under the AzureMediaServices object. 
        Response<JobInner> job = ams.getJobs().createWithResponse(resourceGroupName, accountName, transformName, jobName, jobInner,new Context("key", "value") );

        System.out.printf("/nJob submitted: %s", job.getValue().name());
        

    }
}
