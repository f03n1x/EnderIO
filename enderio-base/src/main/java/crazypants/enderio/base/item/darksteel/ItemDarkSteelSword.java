package crazypants.enderio.base.item.darksteel;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.client.gui.IAdvancedTooltipProvider;
import com.enderio.core.client.handlers.SpecialTooltipHandler;
import com.enderio.core.common.transform.EnderCoreMethods.IOverlayRenderAware;
import com.enderio.core.common.util.ItemUtil;
import com.enderio.core.common.util.OreDictionaryHelper;
import com.google.common.collect.Multimap;

import crazypants.enderio.api.teleport.IItemOfTravel;
import crazypants.enderio.api.teleport.TravelSource;
import crazypants.enderio.api.upgrades.IDarkSteelItem;
import crazypants.enderio.api.upgrades.IDarkSteelUpgrade;
import crazypants.enderio.api.upgrades.IEquipmentData;
import crazypants.enderio.base.EnderIO;
import crazypants.enderio.base.EnderIOTab;
import crazypants.enderio.base.config.Config;
import crazypants.enderio.base.config.config.DarkSteelConfig;
import crazypants.enderio.base.handler.darksteel.DarkSteelRecipeManager;
import crazypants.enderio.base.handler.darksteel.SwordHandler;
import crazypants.enderio.base.init.IModObject;
import crazypants.enderio.base.item.darksteel.attributes.DarkSteelAttributeModifiers;
import crazypants.enderio.base.item.darksteel.attributes.EquipmentData;
import crazypants.enderio.base.item.darksteel.upgrade.energy.EnergyUpgrade;
import crazypants.enderio.base.item.darksteel.upgrade.energy.EnergyUpgrade.EnergyUpgradeHolder;
import crazypants.enderio.base.item.darksteel.upgrade.energy.EnergyUpgradeManager;
import crazypants.enderio.base.item.darksteel.upgrade.travel.TravelUpgrade;
import crazypants.enderio.base.render.itemoverlay.PowerBarOverlayRenderHelper;
import crazypants.enderio.base.teleport.TravelController;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemDarkSteelSword extends ItemSword implements IAdvancedTooltipProvider, IDarkSteelItem, IItemOfTravel, IOverlayRenderAware {

  public static ItemDarkSteelSword createEndSteel(@Nonnull IModObject modObject) {
    ItemDarkSteelSword res = new ItemDarkSteelSword(modObject, EquipmentData.END_STEEL);
    return res;
  }

  public static ItemDarkSteelSword createDarkSteel(@Nonnull IModObject modObject) {
    ItemDarkSteelSword res = new ItemDarkSteelSword(modObject, EquipmentData.DARK_STEEL);
    return res;
  }

  private final int powerPerDamagePoint;
  private long lastBlickTick = -1;
  private final @Nonnull IEquipmentData data;

  public ItemDarkSteelSword(@Nonnull IModObject modObject, @Nonnull IEquipmentData data) {
    super(data.getToolMaterial());
    setCreativeTab(EnderIOTab.tabEnderIOItems);
    modObject.apply(this);
    this.data = data;
    powerPerDamagePoint = DarkSteelConfig.energyUpgradePowerStorageEmpowered.get(0).get() / data.getToolMaterial().getMaxUses();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> list) {
    if (isInCreativeTab(tab)) {
      ItemStack is = new ItemStack(this);
      list.add(is);

      is = new ItemStack(this);
      if (EnergyUpgrade.UPGRADES.get(4).canAddToItem(is, this)) {
        EnergyUpgrade.UPGRADES.get(4).addToItem(is, this);
      } else {
        EnergyUpgrade.UPGRADES.get(3).addToItem(is, this);
      }
      EnergyUpgradeManager.setPowerFull(is, this);
      TravelUpgrade.INSTANCE.addToItem(is, this);
      list.add(is);
    }
  }

  @Override
  public int getIngotsRequiredForFullRepair() {
    return 3;
  }

  @Override
  public boolean isItemForRepair(@Nonnull ItemStack right) {
    return OreDictionaryHelper.hasName(right, data.getRepairIngotOredict());
  }

  @Override
  @Nonnull
  public Multimap<String, AttributeModifier> getAttributeModifiers(@Nonnull EntityEquipmentSlot equipmentSlot, @Nonnull ItemStack stack) {
    Multimap<String, AttributeModifier> res = super.getItemAttributeModifiers(equipmentSlot);
    if (equipmentSlot == EntityEquipmentSlot.MAINHAND) {
      if (Config.darkSteelSwordPowerUsePerHit <= 0 || EnergyUpgradeManager.getEnergyStored(stack) >= Config.darkSteelSwordPowerUsePerHit) {
        EnergyUpgrade energyUpgrade = EnergyUpgrade.loadAnyFromItem(stack);
        int level = energyUpgrade.getLevel();
        res.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), DarkSteelAttributeModifiers.getAttackDamage(level));
        res.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), DarkSteelAttributeModifiers.getAttackSpeed(level));
      }
    }
    return res;
  }

  @Override
  public boolean hitEntity(@Nonnull ItemStack stack, @Nonnull EntityLivingBase entity, @Nonnull EntityLivingBase playerEntity) {

    if (playerEntity instanceof EntityPlayer) {

      EntityPlayer player = (EntityPlayer) playerEntity;
      ItemStack sword = player.getHeldItemMainhand();

      // Durability damage
      EnergyUpgradeHolder eu = EnergyUpgradeManager.loadFromItem(stack);
      if (eu != null && eu.getUpgrade().isAbsorbDamageWithPower() && eu.getEnergy() > 0) {
        eu.extractEnergy(powerPerDamagePoint, false);

      } else {
        super.hitEntity(stack, entity, playerEntity);
      }

      // sword hit
      if (eu != null) {
        eu.writeToItem(sword, this);

        if (eu.getEnergy() >= Config.darkSteelSwordPowerUsePerHit) {
          extractInternal(player.getHeldItemMainhand(), Config.darkSteelSwordPowerUsePerHit);
          entity.getEntityData().setBoolean(SwordHandler.HIT_BY_DARK_STEEL_SWORD, true);
        }

      }

    }
    return true;
  }

  @Override
  public int getEnergyStored(@Nonnull ItemStack container) {
    return EnergyUpgradeManager.getEnergyStored(container);
  }

  @Override
  public boolean getIsRepairable(@Nonnull ItemStack i1, @Nonnull ItemStack i2) {
    return false;
  }

  @Override
  public void addCommonEntries(@Nonnull ItemStack itemstack, @Nullable EntityPlayer entityplayer, @Nonnull List<String> list, boolean flag) {
    DarkSteelRecipeManager.addCommonTooltipEntries(itemstack, entityplayer, list, flag);
  }

  @Override
  public void addBasicEntries(@Nonnull ItemStack itemstack, @Nullable EntityPlayer entityplayer, @Nonnull List<String> list, boolean flag) {
    DarkSteelRecipeManager.addBasicTooltipEntries(itemstack, entityplayer, list, flag);
  }

  @Override
  public void addDetailedEntries(@Nonnull ItemStack itemstack, @Nullable EntityPlayer entityplayer, @Nonnull List<String> list, boolean flag) {
    if (!SpecialTooltipHandler.showDurability(flag)) {
      list.add(ItemUtil.getDurabilityString(itemstack));
    }
    String str = EnergyUpgradeManager.getStoredEnergyString(itemstack);
    if (str != null) {
      list.add(str);
    }
    DarkSteelRecipeManager.addAdvancedTooltipEntries(itemstack, entityplayer, list, flag);
  }

  @Override
  public boolean isActive(@Nonnull EntityPlayer ep, @Nonnull ItemStack equipped) {
    return isTravelUpgradeActive(ep, equipped);
  }

  @Override
  public void extractInternal(@Nonnull ItemStack equipped, int power) {
    EnergyUpgradeManager.extractEnergy(equipped, this, power, false);
  }

  private boolean isTravelUpgradeActive(@Nonnull EntityPlayer ep, @Nonnull ItemStack equipped) {
    return ep.isSneaking() && TravelUpgrade.INSTANCE.hasUpgrade(equipped);
  }

  @Override
  @Nonnull
  public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
    if (hand == EnumHand.MAIN_HAND) {
      ItemStack stack = player.getHeldItem(hand);
      if (isTravelUpgradeActive(player, stack)) {
        if (world.isRemote) {
          if (TravelController.instance.activateTravelAccessable(stack, hand, world, player, TravelSource.STAFF)) {
            player.swingArm(hand);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
          }
        }

        long ticksSinceBlink = EnderIO.proxy.getTickCount() - lastBlickTick;
        if (ticksSinceBlink < 0) {
          lastBlickTick = -1;
        }
        if (Config.travelStaffBlinkEnabled && world.isRemote && ticksSinceBlink >= Config.travelStaffBlinkPauseTicks) {
          if (TravelController.instance.doBlink(stack, hand, player)) {
            player.swingArm(hand);
            lastBlickTick = EnderIO.proxy.getTickCount();
          }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
      }
    }

    return super.onItemRightClick(world, player, hand);
  }

  @Override
  public void renderItemOverlayIntoGUI(@Nonnull ItemStack stack, int xPosition, int yPosition) {
    PowerBarOverlayRenderHelper.instance_upgradeable.render(stack, xPosition, yPosition);
  }

  @Override
  public boolean shouldCauseReequipAnimation(@Nonnull ItemStack oldStack, @Nonnull ItemStack newStack, boolean slotChanged) {
    return slotChanged || oldStack.getItem() != newStack.getItem();
  }

  @Override
  public boolean isForSlot(@Nonnull EntityEquipmentSlot slot) {
    return slot == EntityEquipmentSlot.MAINHAND;
  }

  @Override
  public boolean hasUpgradeCallbacks(@Nonnull IDarkSteelUpgrade upgrade) {
    return upgrade == TravelUpgrade.INSTANCE;
  }

  @Override
  public @Nonnull IEquipmentData getEquipmentData() {
    return data;
  }

}
