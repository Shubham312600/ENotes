"""Store encrypted per-user AI provider credentials and free-only preference."""
from alembic import op
import sqlalchemy as sa


revision = "0002_ai_provider_credentials"
down_revision = "0001_auth_schema"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "ai_provider_credentials",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("user_id", sa.String(length=36), sa.ForeignKey("auth_users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("provider_id", sa.String(length=16), nullable=False),
        sa.Column("encrypted_key", sa.Text(), nullable=False),
        sa.Column("updated_at", sa.Integer(), nullable=False),
        sa.UniqueConstraint("user_id", "provider_id", name="uq_ai_provider_credential_user_provider"),
    )
    op.create_index("ix_ai_provider_credentials_user_id", "ai_provider_credentials", ["user_id"])
    op.create_table(
        "ai_user_preferences",
        sa.Column("user_id", sa.String(length=36), sa.ForeignKey("auth_users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("free_only", sa.Boolean(), nullable=False, server_default=sa.false()),
    )


def downgrade() -> None:
    op.drop_table("ai_user_preferences")
    op.drop_index("ix_ai_provider_credentials_user_id", table_name="ai_provider_credentials")
    op.drop_table("ai_provider_credentials")
