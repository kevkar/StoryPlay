package com.segroup9.storyplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import java.util.HashMap;

public class StoryPlay extends Group {
    private Array<StoryPage> pages;
    private int currentPage = 0;
    private final TextureAtlas textureAtlas;
    private final Skin skin;
    private boolean live = false;
    private final Group actorGroup;

    public static int HEAD_COUNT = 10;
    public Group liveGroup;
    private Image avatarBtn;
    private Image backBtn;
    private int avatarIdx = 0;

    private final Actor bgColorActor;
    private final HashMap<String, ParticleEffectPool> particleFX;

    public StoryPlay(TextureAtlas atlas, Skin skin, HashMap<String, ParticleEffectPool> particles) {
        textureAtlas = atlas;
        this.skin = skin;
        particleFX = particles;

        actorGroup = new Group();
        bgColorActor = new Actor();
        pages = new Array<>();
        pages.add(new StoryPage());
        super.addActor(bgColorActor);
        super.addActor(actorGroup);

        liveGroup = new Group();
        liveGroup.setTouchable(Touchable.childrenOnly);
        liveGroup.setSize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        backBtn = new Image(atlas.findRegion("back"));
        backBtn.setScale(0.5f);
        backBtn.setOrigin(Align.center);
        backBtn.setTouchable(Touchable.enabled);
        backBtn.addCaptureListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                gotoPage(lookUpPageName("start"));
                return true;
            }
        });
        setAvatar();
        super.addActor(liveGroup);
    }

    private void pickAvatar() {
        actorGroup.addAction( Actions.fadeOut(0.2f));
        avatarBtn.addAction( Actions.fadeOut(0.2f));
        backBtn.addAction( Actions.fadeOut(0.2f));
        for (int i = 0; i < HEAD_COUNT; i++) {
            float y = (0.2f + (i/5) * 0.3f) * liveGroup.getHeight();
            float x = (0.1f + (i%5) * 0.2f * 0.8f) * liveGroup.getWidth();
            final Image avatarChoice = new Image(textureAtlas.findRegion("head" + i));
            avatarChoice.setOrigin(Align.center);
            avatarChoice.setTouchable(Touchable.enabled);
            avatarChoice.setPosition(x, y);
            final int finalI = i;
            avatarChoice.addCaptureListener(new ClickListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    avatarIdx = finalI;
                    setAvatar();
                    return true;
                }
            });
            float d = MathUtils.random(0.5f, 0.85f);
            avatarChoice.addAction(Actions.sequence(
                    Actions.alpha(0), Actions.scaleTo(0.5f, 0.5f),
                    Actions.parallel(
                            Actions.fadeIn(d),
                            Actions.scaleTo(1, 1, d, Interpolation.bounceOut)
                    )
            ));
            liveGroup.addActor(avatarChoice);
        }
        TextActor title = new TextActor("Choose Your Character", skin);
        title.setPosition(0.5f * (Gdx.graphics.getWidth() - title.getWidth()), 0.8f * Gdx.graphics.getHeight());
        title.setColor(1, 1, 0, 1);
        title.addAction(Actions.sequence(Actions.alpha(0), Actions.fadeIn(0.5f)));
        liveGroup.addActor(title);
    }

    private void setAvatar() {
        liveGroup.clearChildren();
        avatarBtn = new Image(textureAtlas.findRegion("head" + avatarIdx));
        avatarBtn.setPosition(10, 10);
        avatarBtn.setScale(0.8f);
        avatarBtn.setTouchable(Touchable.enabled);
        avatarBtn.addCaptureListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pickAvatar();
                return true;
            }
        });
        liveGroup.addActor(avatarBtn);
        avatarBtn.addAction( Actions.sequence(Actions.alpha(0), Actions.fadeIn(0.5f)));
        liveGroup.addActor(backBtn);
        backBtn.addAction( Actions.sequence(Actions.alpha(0), Actions.alpha(0.5f, 0.5f)));
        backBtn.setPosition(10 + avatarBtn.getX() + 0.5f * (avatarBtn.getWidth() + backBtn.getWidth()),
                avatarBtn.getY());
        actorGroup.addAction( Actions.fadeIn(0.5f));
    }

    public void setLive(boolean isLive) {
        if (live && !isLive)
            setAvatar();
        live = isLive;
        liveGroup.setVisible(isLive);
        gotoPage(currentPage);
    }

    public boolean isLive() {
        return live;
    }

    public void saveCurrentPage() {
        // save current page actors
        StoryPage page = pages.get(currentPage);
        page.actorDefs.clear();
        for (Actor child : actorGroup.getChildren())
            page.add(child);
    }

    private void loadPageActors() {
        // particle actors need to free their PooledEffect before being removed
        for (Actor actor : actorGroup.getChildren())
            if (actor instanceof ParticleEffectActor)
                ((ParticleEffectActor)actor).freeEffect();

        // remove all actors from stage and load current page
        actorGroup.clearChildren();
        StoryPage page = pages.get(currentPage);

        // set new page color, smooth transition if live
        if (live) {
            bgColorActor.clearActions();
            bgColorActor.addAction(Actions.color(page.backgroundColor, 1, Interpolation.smooth2));
        } else
            bgColorActor.setColor(page.backgroundColor);

        // load page actors to stage and initialize
        for (final StoryActorDef actorDef : page.actorDefs) {
            final Actor actor;
            TextureAtlas.AtlasRegion reg = textureAtlas.findRegion(actorDef.imageName);
            if (reg == null) {
                System.out.println("Image missing!: " + actorDef.imageName);
                reg = textureAtlas.findRegion("img-missing");
            }

            if ("".equals(actorDef.text) || actorDef.text == null)
                actor = new Image(reg);
            else {
                if (live && particleFX.containsKey(actorDef.text)) {
                    ParticleEffectActor p = new ParticleEffectActor(particleFX.get(actorDef.text).obtain());
                    p.stop();
                    actor = p;
                } else {
                    actor = new TextActor(actorDef.text, skin);
                }
            }

            actor.setUserObject(actorDef);
            actor.setOrigin(Align.center); // center actor origin (default is lower left corner)
            actor.setName(actorDef.imageName);
            actor.setPosition(actorDef.posX, actorDef.posY);
            actor.setRotation(actorDef.rotation);
            actor.setScale(actorDef.scale, Math.abs(actorDef.scale));
            actor.setColor(actorDef.color);

            // if we're live, setup actions on the actor
            if (live) {
                // these are the actions defined on the actor
                SequenceAction seq = Actions.sequence();
                for (ActionDef actionDef : actorDef.actionDefs)
                    seq.addAction(actionDef.getAction());

                // if it's
                if (actor instanceof ParticleEffectActor)
                    seq.addAction(Actions.run(new Runnable() {
                                                  @Override
                                                  public void run() {
                                                      ((ParticleEffectActor)actor).start();
                                                  }
                                              }
                    ));

                // if actor has a target page, make it a button that links to said page
                if (!"".equals(actorDef.targetPage)) {

                    // disable actor touch until after other actions have completed
                    actor.setTouchable(Touchable.disabled);
                    seq.addAction(Actions.touchable(Touchable.enabled));

                    // throb actor to indicate it is a button now
                    float s = actor.getScaleY();
                    seq.addAction(Actions.forever(Actions.sequence(
                            Actions.scaleTo(s*1.05f, s*1.05f, 0.15f, Interpolation.smooth),
                            Actions.scaleTo(s*0.95f, s*0.95f, 0.10f, Interpolation.smooth),
                            Actions.scaleTo(s*1.05f, s*1.05f, 0.10f, Interpolation.smooth),
                            Actions.scaleTo(s*0.95f, s*0.95f, 0.15f, Interpolation.smooth),
                            Actions.delay(0.5f))));
                    actor.addCaptureListener(new ClickListener() {
                        @Override
                        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                            StoryPlay.this.gotoPage(lookUpPageName(actorDef.targetPage));
                            return true;
                        }
                    });
                }
                actor.addAction(seq);
            }
            actorGroup.addActor(actor);
        }

        // if we're live, display the page's narration text
        if (live) {
            TextArea nar = new TextArea(getPageNarration(), skin, "narration");
            nar.setSize(Gdx.graphics.getWidth() - 80f, Gdx.graphics.getHeight());
            nar.setPosition(40f, -40f);
            nar.setTouchable(Touchable.disabled);
            nar.addAction(Actions.sequence(Actions.alpha(0), Actions.fadeIn(0.5f)));
            actorGroup.addActor(nar);
        }
    }

    // use negative index to reload current page
    public void gotoPage(int index) {
        if (index >= 0)
            currentPage = (index + pages.size) % pages.size;
        loadPageActors();
    }

    public void nextPage() {
        gotoPage(currentPage+1);
    }

    public void prevPage() {
        gotoPage(currentPage-1);
    }

    public void insertPage(boolean after) {
        if (after) {
            if (currentPage + 1 >= pages.size)
                pages.add(new StoryPage());
            else
                pages.insert(currentPage + 1, new StoryPage());
            gotoPage(currentPage + 1);
        } else {
            pages.insert(currentPage, new StoryPage());
            gotoPage(currentPage);
        }
    }

    public void removeCurrentPage() {
        pages.removeIndex(currentPage);
        if (pages.size == 0)
            pages.add(new StoryPage());
        gotoPage(currentPage);
    }

    public String getPageName() { return pages.get(currentPage).name; }
    public void setPageName(String name) { pages.get(currentPage).name = name; }
    public Color getBGColor() { return bgColorActor.getColor(); }
    public Color getPageColor() { return pages.get(currentPage).backgroundColor; }
    public void setPageColor(Color color) {
        bgColorActor.setColor(color);
        pages.get(currentPage).backgroundColor.set(color);
    }

    private int lookUpPageName(String searchName) {
        for (int i = 0; i < pages.size; i++) {
            if (pages.get(i).name.equals(searchName))
                return i;
        }
        return 0;
    }

    @Override
    public void addActor(Actor actor) {
        actorGroup.addActor(actor);
        pages.get(currentPage).add(actor);
    }

    // helper method for ui
    public String getPageLabel() {
        return "Page: " + (currentPage+1) + "/" + pages.size;
    }

    public String getPageNarration() {
        return pages.get(currentPage).narration;
    }
    public void setPageNarration(String newNarration) {
        pages.get(currentPage).narration = newNarration;
    }

    public void saveToFile() {
        if (isLive())
            setLive(false);
        saveCurrentPage();
        Json json = new Json();
        json.toJson(pages, Gdx.files.local("storyplay.json"));
    }

    public void loadFromFile() {
        Json json = new Json();
        pages = json.fromJson(Array.class, Gdx.files.local("storyPlay.json"));
        loadPageActors();
    }
}
