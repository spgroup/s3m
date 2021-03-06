/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Bruno Medeiros - modifications, removed OverlayStore requirements
 *******************************************************************************/
package melnorme.lang.ide.ui.preferences; 

import melnorme.lang.ide.core.LangCore; 
import melnorme.lang.ide.ui.LangUIPlugin; 
import melnorme.lang.ide.ui.utils.DialogPageUtils; 

import org.eclipse.core.runtime.Status; 
 
import org.eclipse.jface.preference.IPreferenceStore; 
import org.eclipse.jface.preference.PreferencePage; 
import org.eclipse.swt.widgets.Composite; 
import org.eclipse.swt.widgets.Control; 
import org.eclipse.ui.IWorkbench; 
import org.eclipse.ui.IWorkbenchPreferencePage; 
import org.eclipse.ui.PlatformUI; 

/**
 * Abstract preference page which is used to wrap a
 * {@link melnorme.lang.ide.ui.preferences.IPreferencesComponent}.
 */
public abstract  class  AbstractPreferencesComponentPrefPage  extends PreferencePage  
		implements IWorkbenchPreferencePage {
	
	
	private IPreferencesBlock fConfigurationBlock;
	
	
	public AbstractPreferencesComponentPrefPage(IPreferenceStore store) {
		setDescription();
		setPreferenceStore(store);
		fConfigurationBlock = createPreferencesComponent();
	}
	
	
	protected abstract void setDescription();
	
	protected abstract String getHelpId();
	
	protected abstract IPreferencesBlock createPreferencesComponent();
	
	
	@Override
	public void init(IWorkbench workbench) {
	}
	
	
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), getHelpId());
	}
	
	
	@Override
	protected Control createContents(Composite parent) {
		Control body = fConfigurationBlock.createComponent(parent);
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_5649241343936764980.java
		Dialog.applyDialogFont(body); /*FIXME: BUG here with pixel converter usage. */
=======
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_7204479237393670360.java
		
		fConfigurationBlock.loadFromStore();
		return body;
	}
	
	
	@Override
	public boolean performOk() {
		fConfigurationBlock.saveToStore();
		LangUIPlugin.flushInstanceScope();
		
		return true;
	}
	
	
	protected static final Status NO_STATUS = LangCore.createOkStatus(null);
	
	
	@Override
	public void performDefaults() {
		DialogPageUtils.applyStatusToPreferencePage(NO_STATUS, this);
		
		fConfigurationBlock.loadStoreDefaults();
		
		super.performDefaults();
	}
	
	
	@Override
	public void dispose() {
		fConfigurationBlock.dispose();
		super.dispose();
	}

}

