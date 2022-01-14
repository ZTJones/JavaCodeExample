package com.example;
import java.time.Duration;
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
import com.azure.resourcemanager.mediaservices.models.AacAudio;
import com.azure.resourcemanager.mediaservices.models.AacAudioProfile;
import com.azure.resourcemanager.mediaservices.models.BuiltInStandardEncoderPreset;
import com.azure.resourcemanager.mediaservices.models.Codec;
import com.azure.resourcemanager.mediaservices.models.EncoderNamedPreset;
import com.azure.resourcemanager.mediaservices.models.Format;
import com.azure.resourcemanager.mediaservices.models.H264Complexity;
import com.azure.resourcemanager.mediaservices.models.H264Layer;
import com.azure.resourcemanager.mediaservices.models.H264Video;
import com.azure.resourcemanager.mediaservices.models.JobInput;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutput;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.Layer;
import com.azure.resourcemanager.mediaservices.models.Mp4Format;
import com.azure.resourcemanager.mediaservices.models.Preset;
import com.azure.resourcemanager.mediaservices.models.PresetConfigurations;
import com.azure.resourcemanager.mediaservices.models.StandardEncoderPreset;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpoint;
import com.azure.resourcemanager.mediaservices.models.TransformOutput;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;

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
        String transformName = "CustomTransformJava_Modified";
        String jobName = "TestJavaJob" + uniqueString;
        String outputName = "TestJavaOutput" + uniqueString;
        
        // I just sort of enabled everything on the SAS token in order to get it to work. 
        String sasUrl = dotenv.get ("REMOTESTORAGEACCOUNTSAS");
        String storageURL = "https://" + dotenv.get("STORAGEACCOUNTNAME") + ".blob.core.windows.net";
        String sasToken = dotenv.get("SAS_TOKEN");
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
            .sasToken(sasToken)
            .containerName(test.container())
            .buildClient();
            
        System.out.println(blobContainerClient.getAccountName());
        
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
        
        // This code works, but only works to upload it once, then causes errors. Can fix with a try
        blobClient.uploadFromFile(fileName);

        // Need to create an input asset, an output asset
        JobInputAsset chosenAsset = new JobInputAsset().withAssetName(test.name());
        JobOutputAsset outputAsset = new JobOutputAsset().withAssetName(assetName + "_OUTPUT_Modified");
        
        // You'll need to have an asset ready to "catch" the results. 
        ams.getAssets().createOrUpdate(resourceGroupName, accountName, assetName + "_OUTPUT_Modified", inner);
        
        // JobOutputAsset extends JobOutput, and JobInputAsset extends JobInput
        JobOutput[] outputArr = {outputAsset};
        List<JobOutput> jobOutputList = Arrays.asList(outputArr);
        
        // H264Layer
        H264Layer h264Layer = new H264Layer()
            .withBitrate(3600000)
            .withWidth("1280")
            .withHeight("720")
            .withLabel("HD-3600kbps");        

        H264Layer[] vidLayerArr = {h264Layer};
        List<H264Layer> layers = Arrays.asList(vidLayerArr);
        
        H264Video h264Video = new H264Video()
            .withKeyFrameInterval(Duration.ofSeconds(2))
            .withComplexity(H264Complexity.SPEED)
            .withLayers(layers);  // Note that we incorporate the layers here, in the creation of the Codec type object.
        
        AacAudio aacAudio = new AacAudio()
            .withBitrate(128000)
            .withChannels(2)
            .withProfile(AacAudioProfile.AAC_LC)
            .withSamplingRate(48000);
        
        Codec[] codecArr = {h264Video, aacAudio};
        List<Codec> codecs = Arrays.asList(codecArr);

        // FORMAT
        String filenamePattern = "Video-{Basename}-{Label}-{Bitrate}{Extension}";
        Mp4Format mp4Format = new Mp4Format().withFilenamePattern(filenamePattern);

        Format[] formatArr = {mp4Format};
        List<Format> formats = Arrays.asList(formatArr);
        
        // STANDARDENCODERPRESET or customPreset
        StandardEncoderPreset customPreset = new StandardEncoderPreset().withCodecs(codecs).withFormats(formats);

        // Outputs
        TransformOutput transformOutput = new TransformOutput().withPreset(customPreset);
        List<TransformOutput> outputs = Arrays.asList(transformOutput);

        // Custom Transform
    //    manager.transforms().define(transformName).withExistingMediaService(resourceGroupName, accountName).withOutputs(outputs).create();

        // Running custom job
        // manager.jobs().define("ExperimentalJob").withExistingTransform(resourceGroupName, accountName, transformName).withInput(chosenAsset).withOutputs(jobOutputList);

        // Preset Override Outputs
        JobOutputAsset PO_JobOutputAsset = new JobOutputAsset().withAssetName("PresetOverrideTest").withPresetOverride(customPreset);
        JobOutput[] PO_JobOutput = {PO_JobOutputAsset};

        List<JobOutput> PO_JobOutputAssets = Arrays.asList(PO_JobOutput);

        // Preset Override! Note the basic transform used.
        manager.jobs().define("PresetOverride").withExistingTransform(resourceGroupName, accountName, "StandardEncoding").withInput(chosenAsset).withOutputs(PO_JobOutputAssets);


    }
}
