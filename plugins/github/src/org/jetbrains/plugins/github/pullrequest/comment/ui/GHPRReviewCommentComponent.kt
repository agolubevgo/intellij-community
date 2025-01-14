// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHEditableHtmlPaneHandle
import org.jetbrains.plugins.github.pullrequest.ui.GHTextActions
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


object GHPRReviewCommentComponent {

  const val AVATAR_SIZE = GHUIUtil.AVATAR_SIZE
  const val AVATAR_GAP = 10

  const val GAP_TOP = 4
  const val GAP_BOTTOM = 10

  fun create(project: Project,
             thread: GHPRReviewThreadModel,
             comment: GHPRReviewCommentModel,
             ghostUser: GHUser,
             reviewDataProvider: GHPRReviewDataProvider,
             avatarIconsProvider: GHAvatarIconsProvider,
             suggestedChangeHelper: GHPRSuggestedChangeHelper,
             showResolvedMarker: Boolean = true,
             maxContentWidth: Int = GHUIUtil.TEXT_CONTENT_WIDTH): JComponent {

    val author = comment.author ?: ghostUser
    val avatarLabel = JLabel(avatarIconsProvider.getIcon(author.avatarUrl, AVATAR_SIZE))
    val titlePane = GHPRTimelineItemUIUtil.createTitleTextPane(author, comment.dateCreated).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val pendingLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.pending")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = JBUI.CurrentTheme.Validator.warningBackgroundColor()
    }.andOpaque()
    val resolvedLabel = JBLabel(" ${GithubBundle.message("pull.request.review.comment.resolved")} ", UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
    }.andOpaque()

    val commentWrapper = Wrapper().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      isOpaque = false
    }

    Controller(project,
               thread, comment,
               suggestedChangeHelper,
               pendingLabel, resolvedLabel, commentWrapper,
               showResolvedMarker)

    val editablePaneHandle = GHEditableHtmlPaneHandle(project, commentWrapper, comment::body) {
      reviewDataProvider.updateComment(EmptyProgressIndicator(), comment.id, it)
    }

    val editButton = GHTextActions.createEditButton(editablePaneHandle).apply {
      isVisible = comment.canBeUpdated
    }
    val deleteButton = GHTextActions.createDeleteButton {
      reviewDataProvider.deleteComment(EmptyProgressIndicator(), comment.id)
    }.apply {
      isVisible = comment.canBeDeleted
    }

    val actionsPanel = JPanel(HorizontalLayout(8)).apply {
      isOpaque = false
      isVisible = editButton.isVisible && deleteButton.isVisible

      add(editButton)
      add(deleteButton)
    }

    val title = JPanel(HorizontalLayout(10)).apply {
      isOpaque = false
      add(titlePane)
      add(pendingLabel)
      add(resolvedLabel)
    }

    val maxTextWidth = maxContentWidth.let { it - AVATAR_SIZE - AVATAR_GAP }
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .hideMode(3))

      add(avatarLabel, CC().spanY(2).alignY("top")
        .gapRight("$AVATAR_GAP"))

      add(title, CC().push().split(2)
        .minWidth("0").maxWidth("$maxTextWidth"))

      add(actionsPanel, CC().gapLeft("10:push"))
      add(editablePaneHandle.panel, CC().push().grow().newline()
        .gapTop("4")
        .minWidth("0").maxWidth("$maxTextWidth"))
    }.also {
      GHPRTimelineItemUIUtil.actionsVisibleOnHover(it, actionsPanel)
    }
  }

  private class Controller(private val project: Project,
                           private val thread: GHPRReviewThreadModel,
                           private val comment: GHPRReviewCommentModel,
                           private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                           private val pendingLabel: JComponent,
                           private val resolvedLabel: JComponent,
                           private val commentWrapper: Wrapper,
                           private val showResolvedMarker: Boolean) {
    init {
      comment.addChangesListener {
        update()
      }
      update()
    }

    private fun update() {
      val commentComponent = createCommentBodyComponent(project, suggestedChangeHelper, thread, comment.body)
      commentWrapper.setContent(commentComponent)
      commentWrapper.repaint()

      when (comment.state) {
        GHPullRequestReviewCommentState.PENDING -> {
          pendingLabel.isVisible = true
        }

        GHPullRequestReviewCommentState.SUBMITTED -> {
          pendingLabel.isVisible = false
        }
      }

      resolvedLabel.isVisible = comment.isFirstInResolvedThread && showResolvedMarker
    }
  }

  fun createCommentBodyComponent(project: Project,
                                 suggestedChangeHelper: GHPRSuggestedChangeHelper,
                                 thread: GHPRReviewThreadModel,
                                 commentBody: @Nls String): JComponent {
    val commentComponentFactory = GHPRReviewCommentComponentFactory(project)
    val commentComponent = if (GHSuggestedChange.containsSuggestedChange(commentBody)) {
      val suggestedChange = GHSuggestedChange.create(commentBody,
                                                     thread.diffHunk, thread.filePath,
                                                     thread.startLine ?: thread.line, thread.line)
      commentComponentFactory.createCommentWithSuggestedChangeComponent(thread, suggestedChange, suggestedChangeHelper)
    }
    else {
      commentComponentFactory.createCommentComponent(commentBody)
    }
    return commentComponent
  }

  fun factory(project: Project,
              thread: GHPRReviewThreadModel,
              ghostUser: GHUser,
              reviewDataProvider: GHPRReviewDataProvider,
              avatarIconsProvider: GHAvatarIconsProvider,
              suggestedChangeHelper: GHPRSuggestedChangeHelper,
              showResolvedMarkerOnFirstComment: Boolean = true,
              maxContentWidth: Int = GHUIUtil.TEXT_CONTENT_WIDTH,
              postProcessor: (JComponent) -> JComponent = { it })
    : (GHPRReviewCommentModel) -> JComponent {
    return { comment ->
      create(
        project,
        thread, comment, ghostUser,
        reviewDataProvider, avatarIconsProvider,
        suggestedChangeHelper,
        showResolvedMarkerOnFirstComment,
        maxContentWidth).let(postProcessor)
    }
  }
}
