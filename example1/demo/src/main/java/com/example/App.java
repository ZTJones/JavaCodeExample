package com.example;


import java.util.Arrays;
import java.util.List;

// As a heads up, many of these were explored but not used.
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.TransformInner;
import com.azure.resourcemanager.mediaservices.implementation.AssetsClientImpl;
import com.azure.resourcemanager.mediaservices.implementation.AzureMediaServicesBuilder;
import com.azure.resourcemanager.mediaservices.implementation.AzureMediaServicesImpl;
import com.azure.resourcemanager.mediaservices.models.Asset;
import com.azure.resourcemanager.mediaservices.models.BuiltInStandardEncoderPreset;
import com.azure.resourcemanager.mediaservices.models.EncoderNamedPreset;
import com.azure.resourcemanager.mediaservices.models.InputDefinition;
import com.azure.resourcemanager.mediaservices.models.JobInput;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutput;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.MediaService;
import com.azure.resourcemanager.mediaservices.models.Mediaservices;
import com.azure.resourcemanager.mediaservices.models.Preset;
import com.azure.resourcemanager.mediaservices.models.PresetConfigurations;
import com.azure.resourcemanager.mediaservices.models.Properties;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpoint;
import com.azure.resourcemanager.mediaservices.models.Transform;
import com.azure.resourcemanager.mediaservices.models.TransformOutput;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.ctc.wstx.shaded.msv_core.datatype.xsd.BuiltinAtomicType;
/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        // I'm using VS Code credentials.
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);

        
        MediaServicesManager manager = MediaServicesManager.authenticate(credential, profile);

        // Config info, and random variables.  Please make sure you change these to 
        String resourceGroupName = "contractortemp";
        String accountName = "zjonestemp";
        String assetName = "JavaTester1";

        String fileName = "SampleVideo_1280x720_10mb.mp4"; // This is actually supposed to be a path, but I just placed file in project root
        String blobName = "movie.mp4"; // This is the name of the blob we transfer upload our video into

        String transformName = "CustomTransformJava";

        String jobName = "TestJavaJob";

        String outputName = "TestJavaOutput";

        // I just sort of enabled everything on the SAS token in order to get it to work. 
        String SAS = "<SAS Token here>";
        String storageURL = "<Your Storage URL>";

        // Getting list of streaming endpoints, then printing them as an example of Iterables
        PagedIterable<StreamingEndpoint> endpoints = manager.streamingEndpoints().list(resourceGroupName, accountName);
        
        for(StreamingEndpoint endpoint : endpoints){
            System.out.println("Testing loop " + endpoint.name() );
        }
        
        // Now we're going to work on posting a job, and all that entails
        // We start by creating an Azure Media Services client using the serviceClient function from manager.
        AzureMediaServices ams = manager.serviceClient();
        
        // We create a blank slate of an Asset
        AssetInner inner = new AssetInner();
        
        // We then create (or update) an asset. It returns an Asset, which is stored in the AssetInner "test." 
        AssetInner test = ams.getAssets().createOrUpdate(resourceGroupName, accountName, assetName, inner); // This doesn't change "inner." 
        System.out.println(test.container());
        
        // Next, we need to change gears and head over to storage SDK in order to upload our video.
        // Let's try to authenticate. 
        BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
        .endpoint(storageURL)
        .sasToken(SAS)
            .containerName(test.container())
            .buildClient();
            
        System.out.println(blobContainerClient.getAccountName());
        
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        
        // This code works, but only works to upload it once, then causes errors. Can fix with a try
        // blobClient.uploadFromFile(fileName);
        
        // Create preset
        EncoderNamedPreset namedPreset = EncoderNamedPreset.AACGOOD_QUALITY_AUDIO;
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
        ams.getJobs().create(resourceGroupName, accountName, transformName, jobName, jobInner);

    }
}
