package com.minecolonies.coremod.client.gui.citizen;

import com.ldtteam.blockout.Alignment;
import com.ldtteam.blockout.Pane;
import com.ldtteam.blockout.PaneBuilders;
import com.ldtteam.blockout.controls.Image;
import com.ldtteam.blockout.controls.Text;
import com.ldtteam.blockout.views.View;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.constant.HappinessConstants;
import com.minecolonies.coremod.client.gui.AbstractWindowSkeleton;
import com.minecolonies.coremod.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.coremod.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.minecolonies.api.util.constant.WindowConstants.*;
import static com.minecolonies.coremod.client.gui.modules.WindowBuilderResModule.BLACK;
import static com.minecolonies.coremod.entity.citizen.citizenhandlers.CitizenExperienceHandler.PRIMARY_DEPENDENCY_SHARE;
import static com.minecolonies.coremod.entity.citizen.citizenhandlers.CitizenExperienceHandler.SECONDARY_DEPENDENCY_SHARE;

/**
 * Window for the citizen.
 */
public class CitizenWindowUtils
{
    /**
     * Private con to hide public.
     */
    private CitizenWindowUtils()
    {
        // Intentionally left empty.
    }

    /**
     * Enum for the available hearts
     */
    private enum HeartsEnum
    {
        EMPTY(Screen.GUI_ICONS_LOCATION, EMPTY_HEART_ICON_X, HEART_ICON_MC_Y, EMPTY_HEART_VALUE, null, null),
        HALF_RED(Screen.GUI_ICONS_LOCATION, HALF_RED_HEART_ICON_X, HEART_ICON_MC_Y, RED_HEART_VALUE - 1, null, EMPTY),
        RED(Screen.GUI_ICONS_LOCATION, RED_HEART_ICON_X, HEART_ICON_MC_Y, RED_HEART_VALUE, HALF_RED, EMPTY),
        HALF_GOLDEN(Screen.GUI_ICONS_LOCATION, HALF_GOLD_HEART_ICON_X, HEART_ICON_MC_Y, GOLDEN_HEART_VALUE - 1, null, RED),
        GOLDEN(Screen.GUI_ICONS_LOCATION, GOLD_HEART_ICON_X, HEART_ICON_MC_Y, GOLDEN_HEART_VALUE, HALF_GOLDEN, RED),
        HALF_GREEN(GREEN_BLUE_ICON, GREEN_HALF_HEART_ICON_X, GREEN_HEARTS_ICON_Y, GREEN_HEART_VALUE - 1, null, GOLDEN),
        GREEN(GREEN_BLUE_ICON, GREEN_HEART_ICON_X, GREEN_HEARTS_ICON_Y, GREEN_HEART_VALUE, HALF_GREEN, GOLDEN),
        HALF_BLUE(GREEN_BLUE_ICON, BLUE_HALF_HEART_ICON_X, BLUE_HEARTS_ICON_Y, BLUE_HEART_VALUE - 1, null, GREEN),
        BLUE(GREEN_BLUE_ICON, BLUE_HEART_ICON_X, BLUE_HEARTS_ICON_Y, BLUE_HEART_VALUE, HALF_BLUE, GREEN);

        public final int              X;
        public final int              Y;
        public final int              hpValue;
        public final HeartsEnum       prevHeart;
        public final HeartsEnum       halfHeart;
        public       boolean          isHalfHeart = false;
        public final ResourceLocation Image;

        HeartsEnum(
          final ResourceLocation heartImage, final int x, final int y, final int hpValue,
          final HeartsEnum halfHeart, final HeartsEnum prevHeart)
        {
            this.Image = heartImage;
            this.X = x;
            this.Y = y;
            this.hpValue = hpValue;
            this.halfHeart = halfHeart;
            if (halfHeart == null)
            {
                isHalfHeart = true;
            }
            this.prevHeart = prevHeart;
        }
    }

    /**
     * Creates an health bar according to the citizen maxHealth and currentHealth.
     *
     * @param citizen       the citizen.
     * @param healthBarView the health bar view.
     */
    public static void createHealthBar(final ICitizenDataView citizen, final View healthBarView)
    {
        int health = (int) citizen.getHealth();

        healthBarView.setAlignment(Alignment.MIDDLE_RIGHT);
        healthBarView.findPaneOfTypeByID(WINDOW_ID_HEALTHLABEL, Text.class).setText(Integer.toString(health / 2));

        // Add Empty heart background
        for (int i = 0; i < MAX_HEART_ICONS; i++)
        {
            addHeart(healthBarView, i, HeartsEnum.EMPTY);
        }

        // Current Heart we're filling
        int heartPos = 0;

        // Order we're filling the hearts with from high to low
        final List<HeartsEnum> heartList = new ArrayList<>();
        heartList.add(HeartsEnum.BLUE);
        heartList.add(HeartsEnum.GREEN);
        heartList.add(HeartsEnum.GOLDEN);
        heartList.add(HeartsEnum.RED);

        // Iterate through hearts
        for (final HeartsEnum heart : heartList)
        {
            if (heart.isHalfHeart || heart.prevHeart == null)
            {
                continue;
            }

            // Add full hearts
            for (int i = heartPos; i < MAX_HEART_ICONS && health > (heart.prevHeart.hpValue * MAX_HEART_ICONS + 1); i++)
            {
                addHeart(healthBarView, heartPos, heart);
                health -= (heart.hpValue - heart.prevHeart.hpValue);
                heartPos++;
            }

            // Add half heart
            if (health % 2 == 1 && heartPos < MAX_HEART_ICONS && heart.halfHeart != null && health > heart.prevHeart.hpValue * MAX_HEART_ICONS)
            {
                addHeart(healthBarView, heartPos, heart.prevHeart);
                addHeart(healthBarView, heartPos, heart.halfHeart);

                health -= (heart.halfHeart.hpValue - heart.prevHeart.hpValue);
                heartPos++;
            }
            // Finished
            if (heartPos >= MAX_HEART_ICONS)
            {
                return;
            }
        }
    }

    /**
     * Adds a heart to the healthbarView at the given Position
     *
     * @param healthBarView the health bar to add the heart to.
     * @param heartPos      the number of the heart to add.
     * @param heart         the heart to add.
     */
    private static void addHeart(final View healthBarView, final int heartPos, final HeartsEnum heart)
    {
        @NotNull final Image heartImage = new Image();
        heartImage.setImage(heart.Image, heart.X, heart.Y, HEART_ICON_HEIGHT_WIDTH, HEART_ICON_HEIGHT_WIDTH, false);
        heartImage.setPosition(heartPos * HEART_ICON_POS_X + HEART_ICON_OFFSET_X, HEART_ICON_POS_Y);
        healthBarView.addChild(heartImage);
    }

    /**
     * Get vertical offset for the saturation icon based on the iteration
     * If i >= 10, move the icons down another line
     * @param i the current iteration
     * @return the y offset
     */
    private static int getYOffset(final int i)
    {
        return (i >= 10 ? SATURATION_ICON_POS_Y : 0);
    }

    /**
     * Get horizontal offset modifier for the saturation icon based on the iteration
     * if i >= 10, decrease i by 10 to start the line from the beginning
     * @param i the current iteration
     * @return the x offset modifier
     */
    private static int getXOffsetModifier(final int i)
    {
        return (i >= 10 ? i - 10 : i);
    }

    /**
     * Creates an health bar according to the citizen maxHealth and currentHealth.
     *
     * @param citizen the citizen.
     * @param view    the view to add these to.
     */
    public static void createSaturationBar(final ICitizenDataView citizen, final View view)
    {
        view.findPaneOfTypeByID(WINDOW_ID_SATURATION_BAR, View.class).setAlignment(Alignment.MIDDLE_RIGHT);

        //Max saturation (Black food items).
        for (int i = 0; i < ICitizenData.MAX_SATURATION; i++)
        {
            @NotNull final Image saturation = new Image();
            saturation.setImage(Screen.GUI_ICONS_LOCATION,
              EMPTY_SATURATION_ITEM_ROW_POS,
              SATURATION_ICON_COLUMN,
              SATURATION_ICON_HEIGHT_WIDTH,
              SATURATION_ICON_HEIGHT_WIDTH,
              false);

            saturation.setPosition(getXOffsetModifier(i) * SATURATION_ICON_POS_X + SATURATION_ICON_OFFSET_X, SATURATION_ICON_POS_Y + getYOffset(i));
            view.findPaneOfTypeByID(WINDOW_ID_SATURATION_BAR, View.class).addChild(saturation);
        }

        //Current saturation (Full food hearts).
        int saturationPos;
        for (saturationPos = 0; saturationPos < ((int) citizen.getSaturation()); saturationPos++)
        {
            @NotNull final Image saturation = new Image();
            saturation.setImage(Screen.GUI_ICONS_LOCATION, FULL_SATURATION_ITEM_ROW_POS, SATURATION_ICON_COLUMN, SATURATION_ICON_HEIGHT_WIDTH, SATURATION_ICON_HEIGHT_WIDTH, false);
            saturation.setPosition(getXOffsetModifier(saturationPos) * SATURATION_ICON_POS_X + SATURATION_ICON_OFFSET_X, SATURATION_ICON_POS_Y + getYOffset(saturationPos));
            view.findPaneOfTypeByID(WINDOW_ID_SATURATION_BAR, View.class).addChild(saturation);
        }

        //Half food items.
        if (citizen.getSaturation() / 2 % 1 > 0)
        {
            @NotNull final Image saturation = new Image();
            saturation.setImage(Screen.GUI_ICONS_LOCATION, HALF_SATURATION_ITEM_ROW_POS, SATURATION_ICON_COLUMN, SATURATION_ICON_HEIGHT_WIDTH, SATURATION_ICON_HEIGHT_WIDTH, false);
            saturation.setPosition(getXOffsetModifier(saturationPos) * SATURATION_ICON_POS_X + SATURATION_ICON_OFFSET_X, SATURATION_ICON_POS_Y + getYOffset(saturationPos));
            view.findPaneOfTypeByID(WINDOW_ID_SATURATION_BAR, View.class).addChild(saturation);
        }
    }

    /**
     * Creates an Happiness bar according to the citizen maxHappiness and currentHappiness.
     *
     * @param citizen pointer to the citizen data view
     * @param window  pointer to the current window
     */
    public static void createHappinessBar(final ICitizenDataView citizen, final AbstractWindowSkeleton window)
    {
        //Calculates how much percent of the next level has been completed. 
        final double experienceRatio = (citizen.getHappiness() / HappinessConstants.MAX_HAPPINESS) * XP_BAR_WIDTH;
        window.findPaneOfTypeByID(WINDOW_ID_HAPPINESS_BAR, View.class).setAlignment(Alignment.MIDDLE_RIGHT);
        window.findPaneOfTypeByID(WINDOW_ID_HAPPINESS, Text.class).setText(Integer.toString((int) citizen.getHappiness()));

        @NotNull final Image xpBar = new Image();
        xpBar.setImage(Screen.GUI_ICONS_LOCATION, XP_BAR_ICON_COLUMN, HAPPINESS_BAR_EMPTY_ROW, XP_BAR_WIDTH, XP_HEIGHT, false);
        xpBar.setPosition(LEFT_BORDER_X, LEFT_BORDER_Y);

        @NotNull final Image xpBar2 = new Image();
        xpBar2.setImage(Screen.GUI_ICONS_LOCATION, XP_BAR_ICON_COLUMN_END, HAPPINESS_BAR_EMPTY_ROW, XP_BAR_ICON_COLUMN_END_WIDTH, XP_HEIGHT, false);
        xpBar2.setPosition(XP_BAR_ICON_END_OFFSET + LEFT_BORDER_X, LEFT_BORDER_Y);

        window.findPaneOfTypeByID(WINDOW_ID_HAPPINESS_BAR, View.class).addChild(xpBar);
        window.findPaneOfTypeByID(WINDOW_ID_HAPPINESS_BAR, View.class).addChild(xpBar2);

        if (experienceRatio > 0)
        {
            @NotNull final Image xpBarFull = new Image();
            xpBarFull.setImage(Screen.GUI_ICONS_LOCATION, XP_BAR_ICON_COLUMN, HAPPINESS_BAR_FULL_ROW, (int) experienceRatio, XP_HEIGHT, false);
            xpBarFull.setPosition(LEFT_BORDER_X, LEFT_BORDER_Y);
            window.findPaneOfTypeByID(WINDOW_ID_HAPPINESS_BAR, View.class).addChild(xpBarFull);
        }
    }

    /**
     * Fills the citizen gui with it's skill values.
     *  @param citizen the citizen to use.
     * @param window  the window to fill.
     */
    public static void createSkillContent(final ICitizenDataView citizen, final AbstractWindowSkeleton window)
    {
        final boolean isCreative = Minecraft.getInstance().player.isCreative();
        for (final Map.Entry<Skill, Tuple<Integer, Double>> entry : citizen.getCitizenSkillHandler().getSkills().entrySet())
        {
            final String id = entry.getKey().name().toLowerCase(Locale.US);
            window.findPaneOfTypeByID(id, Text.class).setText(new StringTextComponent(Integer.toString(entry.getValue().getA())));

            final Pane buttons = window.findPaneByID(id + "_bts");
            if (buttons != null)
            {
                buttons.setEnabled(isCreative);
            }
        }
    }

    /**
     * Update the display for the happiness.
     *
     * @param citizen the citizen to update it for.
     * @param window  the window to add things to.
     */
    public static void updateHappiness(final ICitizenDataView citizen, final AbstractWindowSkeleton window)
    {
        window.findPaneOfTypeByID("happinessModifier", Text.class).setText(LanguageHandler.format("com.minecolonies.coremod.gui.happiness.happinessmodifier"));
        int yPos = 62;
        for (final String name : citizen.getHappinessHandler().getModifiers())
        {
            final double value = citizen.getHappinessHandler().getModifier(name).getFactor();

            final Image image = new Image();
            image.setSize(11, 11);
            image.setPosition(45, yPos);
            window.addChild(image);

            final Text label = new Text();
            label.setSize(136, 11);
            label.setPosition(70, yPos);
            label.setColors(BLACK);
            label.setText(LanguageHandler.format("com.minecolonies.coremod.gui.townhall.happiness." + name));
            window.addChild(label);
            PaneBuilders.tooltipBuilder().hoverPane(label).append(new TranslationTextComponent("com.minecolonies.coremod.gui.townhall.happiness.desc." + name)).build();

            if (value > 1.0)
            {
                image.setImage(GREEN_ICON);
                PaneBuilders.tooltipBuilder()
                    .append(new TranslationTextComponent("com.minecolonies.coremod.gui.happiness.positive"))
                    .hoverPane(image)
                    .build();
            }
            else if (value == 1)
            {
                image.setImage(BLUE_ICON);
                PaneBuilders.tooltipBuilder()
                    .append(new TranslationTextComponent("com.minecolonies.coremod.gui.happiness.neutral"))
                    .hoverPane(image)
                    .build();
            }
            else if (value > 0.75)
            {
                image.setImage(YELLOW_ICON);
                PaneBuilders.tooltipBuilder()
                    .append(new TranslationTextComponent("com.minecolonies.coremod.gui.happiness.slightlynegative"))
                    .hoverPane(image)
                    .build();
            }
            else
            {
                image.setImage(RED_ICON);
                PaneBuilders.tooltipBuilder()
                    .append(new TranslationTextComponent("com.minecolonies.coremod.gui.happiness.negative"))
                    .hoverPane(image)
                    .build();
            }

            yPos += 12;
        }
    }

    /**
     * Update the job page of the citizen.
     *
     * @param citizen       the citizen.
     * @param windowCitizen the window.
     * @param colony        the colony.
     */
    public static void updateJobPage(final ICitizenDataView citizen, final JobWindowCitizen windowCitizen, final IColonyView colony)
    {
        final IBuildingView building = colony.getBuilding(citizen.getWorkBuilding());

        if (building instanceof AbstractBuildingView && building.getBuildingType() != ModBuildings.library && citizen.getJobView() != null)
        {
            final WorkerBuildingModuleView moduleView = building.getModuleViewMatching(WorkerBuildingModuleView.class, m -> m.getJobEntry() == citizen.getJobView().getEntry());
            if (moduleView == null)
            {
                return;
            }

            windowCitizen.findPaneOfTypeByID(JOB_TITLE_LABEL, Text.class).setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.label",
              LanguageHandler.format(citizen.getJob())));
            windowCitizen.findPaneOfTypeByID(JOB_DESC_LABEL, Text.class).setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.desc"));

            final Skill primary = moduleView.getPrimarySkill();
            windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_LABEL, Text.class)
              .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + primary.name().toLowerCase(Locale.US)) + " (100% XP)");
            windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_LABEL + IMAGE_APPENDIX, Image.class)
              .setImage(BASE_IMG_SRC + primary.name().toLowerCase(Locale.US) + ".png");

            if (primary.getComplimentary() != null && primary.getAdverse() != null)
            {
                windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_COM, Text.class)
                  .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + primary.getComplimentary().name().toLowerCase(Locale.US)) + " ("
                                  + PRIMARY_DEPENDENCY_SHARE + "% XP)");
                windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_COM + IMAGE_APPENDIX, Image.class)
                  .setImage(BASE_IMG_SRC + primary.getComplimentary().name().toLowerCase(Locale.US) + ".png");

                windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_ADV, Text.class)
                  .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + primary.getAdverse().name().toLowerCase(Locale.US)) + " (-"
                                  + PRIMARY_DEPENDENCY_SHARE + "% XP)");
                windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_ADV + IMAGE_APPENDIX, Image.class)
                  .setImage(BASE_IMG_SRC + primary.getAdverse().name().toLowerCase(Locale.US) + ".png");
            }

            final Skill secondary = moduleView.getSecondarySkill();
            windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_LABEL, Text.class)
              .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + secondary.name().toLowerCase(Locale.US)) + " (50% XP)");
            windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_LABEL + IMAGE_APPENDIX, Image.class)
              .setImage(BASE_IMG_SRC + secondary.name().toLowerCase(Locale.US) + ".png");

            if (secondary.getComplimentary() != null && secondary.getAdverse() != null)
            {
                windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_COM, Text.class)
                  .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + secondary.getComplimentary().name().toLowerCase(Locale.US)) + " ("
                                  + SECONDARY_DEPENDENCY_SHARE + "% XP)");
                windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_COM + IMAGE_APPENDIX, Image.class)
                  .setImage(BASE_IMG_SRC + secondary.getComplimentary().name().toLowerCase(Locale.US) + ".png");

                windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_ADV, Text.class)
                  .setText(LanguageHandler.format("com.minecolonies.coremod.gui.citizen.job.skills." + secondary.getAdverse().name().toLowerCase(Locale.US)) + " (-"
                                  + SECONDARY_DEPENDENCY_SHARE + "% XP)");
                windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_ADV + IMAGE_APPENDIX, Image.class)
                  .setImage(BASE_IMG_SRC + secondary.getAdverse().name().toLowerCase(Locale.US) + ".png");
            }
        }
        else
        {
            windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_LABEL + IMAGE_APPENDIX, Image.class).hide();
            windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_COM + IMAGE_APPENDIX, Image.class).hide();
            windowCitizen.findPaneOfTypeByID(PRIMARY_SKILL_ADV + IMAGE_APPENDIX, Image.class).hide();
            windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_LABEL + IMAGE_APPENDIX, Image.class).hide();
            windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_COM + IMAGE_APPENDIX, Image.class).hide();
            windowCitizen.findPaneOfTypeByID(SECONDARY_SKILL_ADV + IMAGE_APPENDIX, Image.class).hide();
        }
    }
}
