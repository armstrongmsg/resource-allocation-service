package cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk;

import cloud.fogbow.common.exceptions.UnexpectedException;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineSize;
import com.microsoft.azure.management.compute.VirtualMachineSizes;
import com.microsoft.azure.management.compute.VirtualMachines;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Optional;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AzureVirtualMachineSDK.class})
public class AzureVirtualMachineSDKTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    // test case: When calling the getVirtualMachineById method and finds a virtual machine,
    // it must verify if It returns a Optional with a virtual machine.
    @Test
    public void testGetVirtualMachineByIdSuccessfullyWhenFindVirtualMachine() throws Exception {
        // set up
        Azure azure = null;
        String virtualMachineId = "virtualMachineId";
        VirtualMachines virtualMachineObject = Mockito.mock(VirtualMachines.class);
        VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
        Mockito.when(virtualMachineObject.getById(Mockito.eq(virtualMachineId)))
                .thenReturn(virtualMachine);
        PowerMockito.spy(AzureVirtualMachineSDK.class);
        PowerMockito.doReturn(virtualMachineObject)
                .when(AzureVirtualMachineSDK.class, "getVirtualMachinesObject", Mockito.eq(azure));

        // exercise
        Optional<VirtualMachine> virtualMachineOptional =
                AzureVirtualMachineSDK.getVirtualMachineById(azure, virtualMachineId);

        // verify
        Assert.assertTrue(virtualMachineOptional.isPresent());
        Assert.assertEquals(virtualMachine, virtualMachineOptional.get());
    }

    // test case: When calling the getVirtualMachineById method and do not find a virtual machine,
    // it must verify if It returns a Optional with a virtual machine.
    @Test
    public void testGetVirtualMachineByIdSuccessfullyWhenNotFindVirtualMachine() throws Exception {
        // set up
        Azure azure = null;
        String virtualMachineId = "virtualMachineId";
        VirtualMachines virtualMachineObject = Mockito.mock(VirtualMachines.class);
        VirtualMachine virtualMachineNull = null;
        Mockito.when(virtualMachineObject.getById(Mockito.eq(virtualMachineId)))
                .thenReturn(virtualMachineNull);
        PowerMockito.spy(AzureVirtualMachineSDK.class);
        PowerMockito.doReturn(virtualMachineObject)
                .when(AzureVirtualMachineSDK.class, "getVirtualMachinesObject", Mockito.eq(azure));

        // exercise
        Optional<VirtualMachine> virtualMachineOptional =
                AzureVirtualMachineSDK.getVirtualMachineById(azure, virtualMachineId);

        // verify
        Assert.assertFalse(virtualMachineOptional.isPresent());
    }

    // test case: When calling the getVirtualMachineById method and throws any exception,
    // it must verify if It throws an UnexpectedException.
    @Test
    public void testGetVirtualMachineByIdFail() throws Exception {
        // set up
        Azure azure = null;
        String virtualMachineId = "virtualMachineId";
        String errorMessage = "error";
        PowerMockito.spy(AzureVirtualMachineSDK.class);
        PowerMockito.doThrow(new RuntimeException(errorMessage))
                .when(AzureVirtualMachineSDK.class, "getVirtualMachinesObject", Mockito.eq(azure));

        // verify
        this.expectedException.expect(UnexpectedException.class);
        this.expectedException.expectMessage(errorMessage);

        // exercise
        AzureVirtualMachineSDK.getVirtualMachineById(azure, virtualMachineId);
    }

    // test case: When calling the getVirtualMachineSizes method,
    // it must verify if It returns a list of virtual machine sizes.
    @Test
    public void testGetVirtualMachineSizesSuccessfully() throws Exception {
        // set up
        Azure azure = null;
        Region region = Region.US_EAST;

        VirtualMachines virtualMachineObject = Mockito.mock(VirtualMachines.class);
        PowerMockito.spy(AzureVirtualMachineSDK.class);
        VirtualMachineSizes sizes = Mockito.mock(VirtualMachineSizes.class);
        PagedList<VirtualMachineSize> virtualMachineSizeExpected = Mockito.mock(PagedList.class);
        Mockito.when(sizes.listByRegion(Mockito.eq(region))).thenReturn(virtualMachineSizeExpected);
        Mockito.when(virtualMachineObject.sizes()).thenReturn(sizes);
        PowerMockito.doReturn(virtualMachineObject)
                .when(AzureVirtualMachineSDK.class, "getVirtualMachinesObject", Mockito.eq(azure));

        // exercise
        PagedList<VirtualMachineSize> virtualMachineSizes =
                AzureVirtualMachineSDK.getVirtualMachineSizes(azure, region);

        // verify
        Assert.assertEquals(virtualMachineSizeExpected, virtualMachineSizes);
    }

    // test case: When calling the getVirtualMachineSizes method ant throws an exception,
    // it must verify if It throws a UnexpectedException.
    @Test
    public void testGetVirtualMachineSizesFail() throws Exception {
        // set up
        Azure azure = null;
        Region region = Region.US_EAST;

        String errorMessage = "error";
        PowerMockito.spy(AzureVirtualMachineSDK.class);
        PowerMockito.doThrow(new RuntimeException(errorMessage))
                .when(AzureVirtualMachineSDK.class, "getVirtualMachinesObject", Mockito.eq(azure));

        // verify
        this.expectedException.expect(UnexpectedException.class);
        this.expectedException.expectMessage(errorMessage);

        // exercise
        AzureVirtualMachineSDK.getVirtualMachineSizes(azure, region);

    }

}
