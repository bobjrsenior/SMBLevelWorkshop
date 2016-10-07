package craftedcart.smblevelworkshop.community.sync;

import craftedcart.smblevelworkshop.community.CommunityRootData;
import craftedcart.smblevelworkshop.community.creator.CommunityUser;
import craftedcart.smblevelworkshop.data.AppDataManager;
import craftedcart.smblevelworkshop.exception.SyncDatabasesException;
import craftedcart.smbworkshopexporter.util.LogHelper;
import io.github.craftedcart.fluidui.uiaction.UIAction1;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

/**
 * @author CraftedCart
 *         Created on 05/10/2016 (DD/MM/YYYY)
 */
public class CloneSyncRootRepoThread implements Runnable {

    private File destDir;
    private String username;
    private UIAction1<Boolean> onFinishAction;

    public CloneSyncRootRepoThread(File destDir, String username, UIAction1<Boolean> onFinishAction) {
        this.destDir = destDir;
        this.username = username;
        this.onFinishAction = onFinishAction;
    }

    @Override
    public void run() {
        try {
            SyncManager.cloneOrPullRepo(destDir, SyncManager.getGitURL(username, "root"));

            if (onFinishAction != null) {
                onFinishAction.execute(true);
            }
        } catch (SyncDatabasesException | IOException e) {
            LogHelper.error(SyncManager.class, "Ignoring user");
            LogHelper.error(SyncManager.class, "\n" + e + "\n" + LogHelper.stackTraceToString(e));

            File communityDir = AppDataManager.getAppSupportDirectory();
            File creatorXMLFile = new File(communityDir, "community/root/CreatorList.xml");

            try {
                CommunityRootData.removeCreator(creatorXMLFile, new CommunityUser(username));
            } catch (ParserConfigurationException | IOException | SAXException | TransformerException e1) {
                LogHelper.error(SyncManager.class, "Error while removing user " + username);
                LogHelper.error(SyncManager.class, "\n" + e1 + "\n" + LogHelper.stackTraceToString(e1));
            }

            if (onFinishAction != null) {
                onFinishAction.execute(false);
            }
        }
    }

}