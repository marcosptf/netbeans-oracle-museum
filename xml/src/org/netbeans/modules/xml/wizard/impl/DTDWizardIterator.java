/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.xml.wizard.impl;

//java imports
import java.awt.Component;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.xml.api.EncodingUtil;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.cookies.EditCookie;
import org.openide.loaders.TemplateWizard;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;

/**
 * Sample schema wizard iterator. See layer.xml for template declaration.
 *
 * @author  Samaresh (Samaresh.Panda@Sun.Com)
 */
public class DTDWizardIterator extends Object implements TemplateWizard.Iterator {
        
    private static final long serialVersionUID = 1L;
    private int index;
    private final Set<ChangeListener> changeListeners = new HashSet<ChangeListener>();
    protected transient WizardDescriptor.Panel[] panels;
    protected String encoding; //project's encoding
        
    /**
     * You should define what panels you want to use here:
     */
    protected WizardDescriptor.Panel[] createPanels (Project project,
						     final TemplateWizard wizard) {
            DataFolder df = null;
            Sources sources = project == null ? null : ProjectUtils.getSources(project);
            SourceGroup[] folders = sources == null ? null : sources.getSourceGroups("java"); //NOI18N
            if (folders == null || folders.length == 0) {
                folders = sources == null ? new SourceGroup[0] : sources.getSourceGroups(Sources.TYPE_GENERIC);
            }
            try {
                df = wizard.getTargetFolder();
            } catch (IOException ex) {
                //just catch
            }
            if (df != null) {
                wizard.setTargetFolder(df);
                org.openide.WizardDescriptor.Panel panel = Templates.createSimpleTargetChooser(project, folders);
                return new org.openide.WizardDescriptor.Panel[]{panel};
            }

            //make the first one as the default target folder. IZ: 98643
            if (folders != null && folders.length != 0) {
                df = DataFolder.findFolder(folders[0].getRootFolder());
                wizard.setTargetFolder(df);
            }
            WizardDescriptor.Panel panel = Templates.createSimpleTargetChooser(project, folders);
            return new WizardDescriptor.Panel[] {panel};
    }
    
    /**
     * Initialization of the wizard iterator.
     */
    public void initialize(TemplateWizard wizard) {
        index = 0;
        Project project = Templates.getProject( wizard );
        panels = createPanels (project, wizard);
        
        // Creating steps.
        Object prop = wizard.getProperty (WizardDescriptor.PROP_CONTENT_DATA); // NOI18N
        String[] beforeSteps = null;
        if (prop instanceof String[]) {
            beforeSteps = (String[])prop;
        }
        String[] steps = createSteps (beforeSteps, panels);
        
        for (int i = 0; i < panels.length; i++) {
            Component c = panels[i].getComponent ();
            if (steps[i] == null) {
                // Default step name to component name of panel.
                // Mainly useful for getting the name of the target
                // chooser to appear in the list of steps.
                steps[i] = c.getName ();
            }
            if (c instanceof JComponent) { // assume Swing components
                JComponent jc = (JComponent) c;
                // Step #.
                jc.putClientProperty (WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, Integer.valueOf(i)); // NOI18N
                // Step name (actually the whole list for reference).
                jc.putClientProperty (WizardDescriptor.PROP_CONTENT_DATA, steps); // NOI18N
            }
        }        
    }

    /**
     * Cleanup.
     */
    public void uninitialize (TemplateWizard wiz) {
        panels = null;
    }

    /**
     * This is where, the schema gets instantiated from the template.
     */
    public Set instantiate (TemplateWizard wizard) throws IOException {        
        FileObject dir = Templates.getTargetFolder( wizard );        
        DataFolder df = DataFolder.findFolder( dir );
        FileObject template = Templates.getTemplate( wizard );        
        DataObject dTemplate = DataObject.find( template );                
        DataObject dobj = dTemplate.createFromTemplate(df, Templates.getTargetName(wizard));
        if (dobj == null)
            return Collections.emptySet();
            
        encoding = EncodingUtil.getProjectEncoding(df.getPrimaryFile());
        if(!EncodingUtil.isValidEncoding(encoding))
            encoding = "UTF-8"; //NOI18N
        EditCookie edit = dobj.getCookie(EditCookie.class);
        if (edit != null) {
            EditorCookie editorCookie = dobj.getCookie(EditorCookie.class);
            Document doc = (Document)editorCookie.openDocument();
            fixEncoding(doc, encoding);
            SaveCookie save = dobj.getCookie(SaveCookie.class);
            if (save!=null) save.save();
        }
        
        return Collections.singleton(dobj.getPrimaryFile());
    }
    
    /**
     *
     */
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     *
     *
     */
    public void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    /**
     *
     */
    public void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);

        Iterator<ChangeListener> i = changeListeners.iterator();
        while (i.hasNext()) {
            try {
                i.next().stateChanged(event);
            } catch (Exception e) {
                //Debug.debugNotify(e);
            }
        }
    }
    
    /**
     *
     */
    public String name () {
        return NbBundle.getMessage(DTDWizardIterator.class, "TITLE_x_of_y",
            Integer.valueOf(index + 1), Integer.valueOf(panels.length));
    }
    
    /**
     *
     */
    public boolean hasNext () {
        return index < panels.length - 1;
    }
    
    /**
     *
     */
    public boolean hasPrevious () {
        return index > 0;
    }
    
    /**
     *
     */
    public void nextPanel () {
        if (! hasNext ()) throw new NoSuchElementException ();
        index++;
    }
    
    /**
     *
     */
    public void previousPanel () {
        if (! hasPrevious ()) throw new NoSuchElementException ();
        index--;
    }
    
    /**
     * Returns the current panel.
     */
    public WizardDescriptor.Panel current () {
        return panels[index];
    }

    /**
     * Create steps.
     */
    private String[] createSteps(String[] before, WizardDescriptor.Panel[] panels) {
        //assert panels != null;
        // hack to use the steps set before this panel processed
        int diff = 0;
        if (before == null) {
            before = new String[0];
        } else if (before.length > 0) {
            diff = ("...".equals (before[before.length - 1])) ? 1 : 0; // NOI18N
        }
        String[] res = new String[ (before.length - diff) + panels.length];
        for (int i = 0; i < res.length; i++) {
            if (i < (before.length - diff)) {
                res[i] = before[i];
            } else {
                res[i] = panels[i - before.length + diff].getComponent ().getName ();
            }
        }
        return res;
    }
    
    /**
     * Utility method to replace text in document.
     */
    void replaceInDocument(javax.swing.text.Document document, String replaceFrom, String replaceTo) {
        javax.swing.text.AbstractDocument doc = (javax.swing.text.AbstractDocument)document;
        int len = replaceFrom.length();
        try {
            String content = doc.getText(0,doc.getLength());
            int index = content.lastIndexOf(replaceFrom);
            while (index>=0) {
                doc.replace(index,len,replaceTo,null);
                content=content.substring(0,index);
                index = content.lastIndexOf(replaceFrom);
            }
        } catch (javax.swing.text.BadLocationException ex){}
    }
    
    void fixEncoding(javax.swing.text.Document document, String encoding) {
        if(encoding == null)
            encoding = "UTF-8"; //NOI18N
        try {
            document.insertString(19, " encoding=\""+encoding+"\"", null);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
}
